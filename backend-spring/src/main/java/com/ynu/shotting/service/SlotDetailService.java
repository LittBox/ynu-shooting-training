package com.ynu.shoting.service;

import com.ynu.shoting.config.Slot;
import com.ynu.shoting.dto.SlotDetailVO;
import com.ynu.shoting.entity.AuditLog;
import com.ynu.shoting.entity.Booking;
import com.ynu.shoting.entity.User;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.AuditLogRepository;
import com.ynu.shoting.repository.BookingRepository;
import com.ynu.shoting.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SlotDetailService {
    private final BookingService bookingService;
    private final BookingRepository bookings;
    private final AuditLogRepository auditLogs;
    private final AuthContext authContext;
    private final Clock clock;

    @Transactional
    public SlotDetailVO detail(HttpServletRequest request, LocalDate date, String slotId) {
        Slot slot;
        try { slot = Slot.fromId(slotId); }
        catch (IllegalArgumentException ex) { throw new BusinessException("无效的时间片"); }

        // Invalid supplied credentials fail closed; guests receive occupancy only.
        String authorization = request.getHeader("Authorization");
        User viewer = authorization == null ? null : authContext.currentUser(request);
        boolean staff = viewer != null && (viewer.getRole() == User.Role.admin || viewer.getRole() == User.Role.superadmin);
        String scope = viewer == null ? "GUEST" : staff ? "STAFF" : "STUDENT";

        Map<Long, List<Booking>> byDevice = bookings.findBySlotDateAndSlotId(date.toString(), slotId).stream()
                .filter(b -> b.getStatus() != Booking.BookingStatus.CANCELLED)
                .sorted(Comparator.comparing(Booking::getId))
                .collect(Collectors.groupingBy(b -> b.getDevice().getId()));

        List<SlotDetailVO.DeviceRow> devices = bookingService.dailySlots(date, null).stream()
                .filter(row -> row.getId().equals(slotId))
                .sorted(Comparator.comparing(row -> row.getDeviceId()))
                .map(row -> new SlotDetailVO.DeviceRow(row.getDeviceId(), row.getDeviceName(), row.getDeviceType(),
                        row.getDeviceStatus(), Boolean.TRUE.equals(row.getStaffed()), row.getAvailable() > 0, Boolean.TRUE.equals(row.getCoachPresent()), Boolean.TRUE.equals(row.getWalkInAvailable()),
                        byDevice.getOrDefault(row.getDeviceId(), List.of()).stream()
                                .map(b -> project(b, viewer, staff)).toList()))
                .toList();

        if (staff) {
            auditLogs.save(AuditLog.builder().admin(viewer).action("VIEW_SLOT_ROSTER")
                    .detail("date=" + date + ";slot=" + slotId).build());
        }
        return new SlotDetailVO(date.toString(), slotId, slot.getStart().toString(), slot.getEnd().toString(),
                scope, Instant.now(clock), devices);
    }

    private SlotDetailVO.Reservation project(Booking booking, User viewer, boolean staff) {
        User owner = booking.getUser();
        String name = null;
        String studentNo = null;
        if (viewer != null) {
            name = owner.getNickname() == null || owner.getNickname().isBlank()
                    ? "学员 · " + booking.getId() : owner.getNickname();
            if (staff && owner.getProfile() != null) {
                name = owner.getProfile().getRealName();
                studentNo = owner.getProfile().getStudentNo();
            }
        }
        return new SlotDetailVO.Reservation(booking.getId(), booking.getStatus().name(), name, studentNo,
                viewer != null && owner.getId().equals(viewer.getId()));
    }
}
