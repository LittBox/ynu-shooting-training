package com.ynu.shoting.service.impl;

import com.ynu.shoting.config.BookingProperties;
import com.ynu.shoting.config.Slot;
import com.ynu.shoting.dto.BookingRequest;
import com.ynu.shoting.dto.BookingVO;
import com.ynu.shoting.dto.SlotVO;
import com.ynu.shoting.entity.Booking;
import com.ynu.shoting.entity.CancellationLog;
import com.ynu.shoting.entity.Device;
import com.ynu.shoting.entity.NoShowRecord;
import com.ynu.shoting.entity.User;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.BookingRepository;
import com.ynu.shoting.repository.DeviceRepository;
import com.ynu.shoting.repository.NoShowRecordRepository;
import com.ynu.shoting.repository.UserRepository;
import com.ynu.shoting.security.AuthContext;
import com.ynu.shoting.service.BookingService;
import com.ynu.shoting.websocket.SlotPushService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import com.ynu.shoting.exception.CheckInExpiredException;
import com.ynu.shoting.service.BookingAccess;
import com.ynu.shoting.repository.AdminScheduleRepository;
import com.ynu.shoting.repository.UserAvailabilityRepository;
import com.ynu.shoting.entity.UserAvailability;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final List<Booking.BookingStatus> OCCUPYING_STATUSES = List.of(
            Booking.BookingStatus.BOOKED,
            Booking.BookingStatus.CHECKED_IN,
            Booking.BookingStatus.IN_USE
    );

    private final com.ynu.shoting.service.VenueAccess venue;
    private final com.ynu.shoting.service.VenueDutyService duty;
    private final com.ynu.shoting.service.TrainingService training;
    private final com.ynu.shoting.service.NoShowService noShows;
    private final Clock clock;
    private final BookingAccess bookingAccess;
    private final AdminScheduleRepository schedules;
    private final UserAvailabilityRepository availability;
    private final BookingRepository bookingRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final NoShowRecordRepository noShowRecordRepository;
    private final com.ynu.shoting.repository.CancellationLogRepository cancellationLogRepository;
    private final AuthContext authContext;
    private final BookingProperties props;
    private final SlotPushService slotPushService;

    @Override
    @Transactional
    public BookingVO create(HttpServletRequest request, BookingRequest req) {
        User user = authContext.currentUser(request);
        venue.lock();
        user = userRepository.findLockedById(user.getId()).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        validateProfileCompleted(user);
        validateSlot(req.getSlotId());

        LocalDate slotDate = LocalDate.parse(req.getSlotDate(), DateTimeFormatter.ISO_DATE);
        LocalDate today = LocalDate.now(clock);

        if (slotDate.isBefore(today)) throw new BusinessException("不能预约过去的日期");
        if (slotDate.isAfter(today.plusDays(props.getAdvanceDays()))) {
            throw new BusinessException("最多只能提前 " + props.getAdvanceDays() + " 天预约");
        }
        if (!LocalDateTime.of(slotDate, Slot.fromId(req.getSlotId()).getStart()).isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException("该时段已开始，请选择其他时段");
        }

        Device device = deviceRepository.findLockedById(req.getDeviceId())
                .orElseThrow(() -> new BusinessException("设备不存在"));
        if (device.getStatus() == Device.DeviceStatus.DISABLED
                || device.getStatus() == Device.DeviceStatus.MAINTENANCE) {
            throw new BusinessException(409, "设备不可预约");
        }

        if (!schedules.existsBySlotDateAndSlotIdAndAdminRoleNot(req.getSlotDate(), req.getSlotId(), User.Role.student))
            throw new BusinessException(409, "该时段场地无教员排班，暂未开放");
        boolean personalAvailable = availability.existsByUserIdAndSlotDateAndSlotId(user.getId(), req.getSlotDate(), req.getSlotId());
        if (!personalAvailable && !req.isAvailabilityConfirmed())
            throw new BusinessException(409, "请确认本人该时段可到场训练");

        // 唯一性：同设备+同时间片+活跃态 只能存在一条
        bookingRepository.findBySlotDateAndSlotIdAndDeviceIdAndStatusIn(
                req.getSlotDate(), req.getSlotId(), req.getDeviceId(), OCCUPYING_STATUSES
        ).ifPresent(b -> { throw new BusinessException(409, "该设备此时间片已被占用"); });

        validateUserQuota(user, slotDate, req.getSlotId());

        if (!personalAvailable) availability.save(UserAvailability.builder().user(user)
                .slotDate(req.getSlotDate()).slotId(req.getSlotId()).build());
        Booking booking = Booking.builder()
                .user(user)
                .device(device)
                .slotDate(req.getSlotDate())
                .slotId(req.getSlotId())
                .status(Booking.BookingStatus.BOOKED)
                .bookedAt(LocalDateTime.now(clock))
                .build();
        booking = bookingRepository.save(booking);
        slotPushService.pushDate(req.getSlotDate());

        return toVO(booking);
    }

    @Override
    @Transactional
    public BookingVO walkIn(HttpServletRequest request, com.ynu.shoting.dto.WalkInRequest req) {
        User user = authContext.currentUser(request);
        venue.lock();
        user = userRepository.findLockedById(user.getId()).orElseThrow(() -> new BusinessException(401, "用户不存在"));
        validateProfileCompleted(user);
        validateSlot(req.getSlotId());
        LocalDate date = LocalDate.parse(req.getSlotDate());
        Slot slot = Slot.fromId(req.getSlotId());
        LocalDateTime now = LocalDateTime.now(clock);
        if (!date.equals(LocalDate.now(clock)) || now.isBefore(LocalDateTime.of(date, slot.getStart()))
                || !now.isBefore(LocalDateTime.of(date, slot.getEnd())))
            throw new BusinessException(409, "仅当前正在进行的时段可立即训练");
        Device device = deviceRepository.findLockedById(req.getDeviceId())
                .orElseThrow(() -> new BusinessException(404, "设备不存在"));
        if (device.getStatus() != Device.DeviceStatus.IDLE && device.getStatus() != Device.DeviceStatus.BOOKED)
            throw new BusinessException(409, "设备当前不可训练");
        duty.requirePresent(req.getSlotDate(), req.getSlotId());

        // Preserve valid reservations; release overdue no-shows under the same device lock.
        for (Booking previous : bookingRepository.findBySlotDateAndSlotId(req.getSlotDate(), req.getSlotId())) {
            if (!previous.getDevice().getId().equals(device.getId())) continue;
            if (previous.getStatus() == Booking.BookingStatus.BOOKED) noShows.expire(previous.getId());
            // Compatibility with old completed records whose unique occupancy key was retained.
            if (previous.getStatus() == Booking.BookingStatus.COMPLETED) previous.setOccupiedDeviceSlot(null);
        }
        bookingRepository.flush();
        bookingRepository.findBySlotDateAndSlotIdAndDeviceIdAndStatusIn(
                req.getSlotDate(), req.getSlotId(), req.getDeviceId(), OCCUPYING_STATUSES)
                .ifPresent(b -> { throw new BusinessException(409, "该设备仍有有效预约或训练，请选择其他设备"); });
        if (bookingRepository.findByUserId(user.getId()).stream().anyMatch(b -> b.getStatus() == Booking.BookingStatus.IN_USE))
            throw new BusinessException(409, "请先结束正在进行的训练");
        validateUserQuota(user, date, req.getSlotId());
        Booking booking = bookingRepository.saveAndFlush(Booking.builder().user(user).device(device)
                .slotDate(req.getSlotDate()).slotId(req.getSlotId()).status(Booking.BookingStatus.CHECKED_IN)
                .bookedAt(now).checkedInAt(now).build());
        training.start(request, booking.getId(), req.getMode());
        return toVO(booking);
    }

    private void validateUserQuota(User user, LocalDate slotDate, String slotId) {
        // 同一用户在同一时间片已有活跃预约
        boolean userConflict = bookingRepository.findActiveByUser(user.getId()).stream()
                .anyMatch(b -> b.getSlotDate().equals(slotDate.toString())
                        && b.getSlotId().equals(slotId));
        if (userConflict) throw new BusinessException(409, "您已在该时间片预约其他设备");

        // 每日 3 次上限
        long daily = bookingRepository.findActiveByUserAndDate(user.getId(), slotDate.toString()).size();
        if (daily >= props.getDailyLimit()) {
            throw new BusinessException(429, "每日最多 " + props.getDailyLimit() + " 次预约");
        }

        // 每周 10 次上限（以 slotDate 所在自然周[周一~周日]统计）
        LocalDate weekStart = slotDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        long weekly = bookingRepository.countActiveByUserInRange(
                user.getId(),
                weekStart.format(DateTimeFormatter.ISO_DATE),
                weekEnd.format(DateTimeFormatter.ISO_DATE));
        if (weekly >= props.getWeeklyLimit()) {
            throw new BusinessException(429, "每周最多 " + props.getWeeklyLimit() + " 次预约");
        }

    }

    @Override
    @Transactional
    public void cancel(HttpServletRequest request, Long bookingId) {
        User user = authContext.currentUser(request);
        Booking booking = bookingAccess.lock(bookingId);
        if (!booking.getUser().getId().equals(user.getId())
                && user.getRole() == User.Role.student) {
            throw new BusinessException(403, "无权取消此预约");
        }
        if (booking.getStatus() != Booking.BookingStatus.BOOKED
                && !(user.getRole() != User.Role.student && booking.getStatus() == Booking.BookingStatus.CHECKED_IN))
            throw new BusinessException(409, "当前预约不可取消；已签到未训练的记录请联系管理员处理");

        // 取消级别判定
        Slot slot = Slot.fromId(booking.getSlotId());
        LocalDateTime slotStart = LocalDateTime.of(
                LocalDate.parse(booking.getSlotDate()), slot.getStart());
        long hoursToStart = Duration.between(LocalDateTime.now(clock), slotStart).toHours();

        CancellationLog.CancelLevel level;
        if (hoursToStart >= props.getCancelFreeHours()) level = CancellationLog.CancelLevel.free;
        else if (hoursToStart >= props.getCancelLateHours()) level = CancellationLog.CancelLevel.late;
        else level = CancellationLog.CancelLevel.near_no_show;

        booking.setStatus(Booking.BookingStatus.CANCELLED);
        booking.setCancelReason("user_cancel");
        bookingRepository.save(booking);

        CancellationLog cancellationLog = CancellationLog.builder()
                .booking(booking).user(booking.getUser()).reason(user.getId().equals(booking.getUser().getId()) ? "user_cancel" : "admin_cancel").level(level).build();
        cancellationLogRepository.save(cancellationLog);
        slotPushService.pushDate(booking.getSlotDate());
        log.info("Booking cancelled id={} level={}", bookingId, level);
    }

    @Override
    @Transactional(noRollbackFor = CheckInExpiredException.class)
    public BookingVO checkIn(HttpServletRequest request, Long bookingId) {
        User user = authContext.currentUser(request);
        Booking booking = bookingAccess.lock(bookingId);
        if (!booking.getUser().getId().equals(user.getId())) {
            throw new BusinessException(403, "无权签到此预约");
        }
        if (booking.getStatus() != Booking.BookingStatus.BOOKED) {
            throw new BusinessException(409, "当前状态不可签到");
        }

        Slot slot = Slot.fromId(booking.getSlotId());
        LocalDateTime slotStart = LocalDateTime.of(
                LocalDate.parse(booking.getSlotDate()), slot.getStart());
        LocalDateTime now = LocalDateTime.now(clock);

        if (now.isBefore(slotStart.minusMinutes(props.getCheckinBeforeMin()))) {
            throw new BusinessException("只能在开始前 " + props.getCheckinBeforeMin() + " 分钟内签到");
        }
        if (now.isAfter(slotStart.plusMinutes(props.getCheckinTimeoutMin()))) {
            // 超时签到：自动标记爽约（constitution 第七条）
            booking.setStatus(Booking.BookingStatus.NO_SHOW);
            bookingRepository.save(booking);
            noShowRecordRepository.save(NoShowRecord.builder()
                    .user(user)
                    .booking(booking)
                    .createdAt(LocalDateTime.now(clock))
                    .build());
            slotPushService.pushDate(booking.getSlotDate());
            throw new CheckInExpiredException();
        }

        booking.setStatus(Booking.BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(LocalDateTime.now(clock));
        return toVO(bookingRepository.save(booking));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingVO> myBookings(HttpServletRequest request) {
        User user = authContext.currentUser(request);
        return bookingRepository.findByUserId(user.getId()).stream()
                .map(this::toVO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SlotVO> dailySlots(LocalDate date, String deviceType) {
        String dateStr = date.format(DateTimeFormatter.ISO_DATE);
        List<Device> devices = (deviceType == null || deviceType.isBlank())
                ? deviceRepository.findAll()
                : deviceRepository.findByType(Device.DeviceType.valueOf(deviceType));

        List<Booking> bookings = bookingRepository.findBySlotDate(dateStr);
        Map<String, List<Booking>> bySlot = bookings.stream()
                .filter(b -> OCCUPYING_STATUSES.contains(b.getStatus()))
                .collect(Collectors.groupingBy(Booking::getSlotId));

        var daySchedules = schedules.findBySlotDate(dateStr);
        LocalDateTime now = LocalDateTime.now(clock);
        Set<String> present = daySchedules.stream().filter(s -> duty.isCovering(s, now))
                .map(s -> s.getSlotId()).collect(Collectors.toSet());
        Set<String> staffed = daySchedules.stream()
                .filter(a -> a.getAdmin().getRole() != User.Role.student)
                .map(a -> a.getSlotId()).collect(Collectors.toSet());
        List<SlotVO> result = new ArrayList<>();
        for (Slot slot : Slot.values()) {
            List<Booking> slotBookings = bySlot.getOrDefault(slot.getId(), List.of());
            for (Device device : devices) {
                List<Booking> deviceBookings = slotBookings.stream()
                        .filter(b -> b.getDevice().getId().equals(device.getId()))
                        .toList();
                boolean ongoing = date.equals(LocalDate.now(clock)) && !now.isBefore(LocalDateTime.of(date, slot.getStart()))
                        && now.isBefore(LocalDateTime.of(date, slot.getEnd()));
                boolean held = deviceBookings.stream().anyMatch(b -> b.getStatus() != Booking.BookingStatus.BOOKED
                        || !now.isAfter(LocalDateTime.of(date, slot.getStart()).plusMinutes(props.getCheckinTimeoutMin())));
                int capacity = 1;  // 单设备单时间片单用户
                int booked = deviceBookings.size();
                // Current physical usage blocks immediate training, not unoccupied future slots.
                boolean reservable = device.getStatus() != Device.DeviceStatus.MAINTENANCE
                        && device.getStatus() != Device.DeviceStatus.DISABLED;
                boolean usableNow = device.getStatus() == Device.DeviceStatus.IDLE
                        || device.getStatus() == Device.DeviceStatus.BOOKED;
                boolean future = LocalDateTime.of(date, slot.getStart()).isAfter(LocalDateTime.now(clock))
                        && !date.isAfter(LocalDate.now(clock).plusDays(props.getAdvanceDays()));
                result.add(SlotVO.builder()
                        .id(slot.getId())
                        .label(slot.getLabel())
                        .start(slot.getStart().toString())
                        .end(slot.getEnd().toString())
                        .deviceId(device.getId())
                        .deviceName(device.getName())
                        .deviceType(device.getType().name())
                        .deviceStatus(device.getStatus().name())
                        .available(reservable && future && staffed.contains(slot.getId()) && booked < capacity ? 1 : 0)
                        .walkInAvailable(usableNow && ongoing && present.contains(slot.getId()) && !held)
                        .staffed(staffed.contains(slot.getId()))
                        .coachPresent(present.contains(slot.getId()))
                        .bookedCount(booked)
                        .capacity(capacity)
                        .bookedBy(List.of())
                        .build());
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public BookingVO detail(HttpServletRequest request, Long bookingId) {
        User user = authContext.currentUser(request);
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BusinessException("预约不存在"));
        if (!booking.getUser().getId().equals(user.getId())
                && user.getRole() == User.Role.student) {
            throw new BusinessException(403, "无权查看此预约");
        }
        return toVO(booking);
    }

    private void validateProfileCompleted(User user) {
        if (user.getProfileStatus() != User.ProfileStatus.completed
                || user.getProfile() == null || !user.getProfile().isComplete()) {
            throw new BusinessException("请先完善姓名、学号、手机号和性别");
        }
    }

    private void validateSlot(String slotId) {
        try {
            Slot.fromId(slotId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("无效的时间片");
        }
    }

    private BookingVO toVO(Booking b) {
        Slot slot = Slot.fromId(b.getSlotId());
        User u = b.getUser();
        return BookingVO.builder()
                .id(b.getId())
                .slotDate(b.getSlotDate())
                .slotId(b.getSlotId())
                .slotLabel(slot.getLabel())
                .slotStart(slot.getStart().toString())
                .slotEnd(slot.getEnd().toString())
                .deviceName(b.getDevice().getName())
                .deviceType(b.getDevice().getType().name())
                .status(b.getStatus().name())
                .sessionId(b.getTrainingSession() == null ? null : b.getTrainingSession().getId())
                .nickname(u.getNickname())
                .realName(u.getProfile() != null ? u.getProfile().getRealName() : null)
                .studentNo(u.getProfile() != null ? u.getProfile().getStudentNo() : null)
                .bookedAt(b.getBookedAt())
                .checkedInAt(b.getCheckedInAt())
                .startedAt(b.getStartedAt())
                .endedAt(b.getEndedAt())
                .build();
    }
}
