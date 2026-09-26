package com.ynu.shoting.service;

import com.ynu.shoting.config.Slot;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.websocket.SlotPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class VenueDutyService {
    private final AdminScheduleRepository schedules;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final TrainingSessionRepository sessions;
    private final AuditLogRepository audits;
    private final VenueAccess venue;
    private final SlotPushService push;
    private final Clock clock;

    public record ScheduleView(Long id, Long adminId, String adminName, String slotDate, String slotId,
                               String state, LocalDateTime arrivedAt, LocalDateTime departedAt,
                               boolean coveringNow, boolean canArrive) {}
    public record DutyView(List<ScheduleView> schedules, long onDutyCount, long activeTrainingCount,
                           boolean hasOpenAttendance) {}

    private boolean arrivalWindow(AdminSchedule s, LocalDateTime now) {
        Slot slot = Slot.fromId(s.getSlotId());
        LocalDate date = LocalDate.parse(s.getSlotDate());
        return date.equals(now.toLocalDate()) && !now.isBefore(date.atTime(slot.getStart()).minusMinutes(30))
                && now.isBefore(date.atTime(slot.getEnd()));
    }

    public boolean isCovering(AdminSchedule s, LocalDateTime now) {
        return s.getAdmin().getRole() != User.Role.student && s.getArrivedAt() != null
                && !s.getArrivedAt().isAfter(now) && s.getDepartedAt() == null && arrivalWindow(s, now);
    }

    private ScheduleView view(AdminSchedule s, Long viewerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        String state = s.getArrivedAt() == null ? "PLANNED" : s.getDepartedAt() == null ? "PRESENT" : "LEFT";
        var profile = s.getAdmin().getProfile();
        String name = profile != null && profile.getRealName() != null && !profile.getRealName().isBlank()
                ? profile.getRealName() : "教练员";
        return new ScheduleView(s.getId(), s.getAdmin().getId(), name, s.getSlotDate(),
                s.getSlotId(), state, s.getArrivedAt(), s.getDepartedAt(), isCovering(s, now),
                s.getAdmin().getId().equals(viewerId) && !state.equals("PRESENT") && arrivalWindow(s, now));
    }

    @Transactional(readOnly = true)
    public List<ScheduleView> list(String date, Long viewerId) {
        LocalDate.parse(date);
        return schedules.findBySlotDate(date).stream()
                .sorted(Comparator.comparing(AdminSchedule::getSlotId).thenComparing(AdminSchedule::getId))
                .map(s -> view(s, viewerId)).toList();
    }

    @Transactional(readOnly = true)
    public DutyView status(String date, Long viewerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        long present = schedules.findBySlotDate(now.toLocalDate().toString()).stream()
                .filter(s -> isCovering(s, now)).map(s -> s.getAdmin().getId()).distinct().count();
        return new DutyView(list(date, viewerId), present, sessions.countByEndedAtIsNull(),
                !schedules.findByAdminIdAndArrivedAtIsNotNullAndDepartedAtIsNull(viewerId).isEmpty());
    }

    public ScheduleView schedule(User actor, Long adminId, String date, String slotId) {
        LocalDate day = LocalDate.parse(date);
        Slot slot = Slot.fromId(slotId);
        if (!day.atTime(slot.getEnd()).isAfter(LocalDateTime.now(clock)))
            throw new BusinessException(400, "不能为已经结束的时段排班");
        venue.lock();
        User coach = users.findById(adminId).orElseThrow(() -> new BusinessException(404, "教员不存在"));
        if (coach.getRole() == User.Role.student) throw new BusinessException(400, "排班人员必须是教员或管理员");
        AdminSchedule s = schedules.findByAdminIdAndSlotDateAndSlotId(adminId, date, slotId)
                .orElseGet(() -> schedules.save(AdminSchedule.builder().admin(coach).slotDate(date).slotId(slotId)
                        .createdAt(LocalDateTime.now(clock)).build()));
        audit(actor, "schedule", "schedule=" + s.getId());
        push.pushDate(date);
        return view(s, actor.getId());
    }

    public void delete(User actor, Long id) {
        venue.lock();
        AdminSchedule s = schedules.findById(id).orElseThrow(() -> new BusinessException(404, "排班不存在"));
        if (s.getArrivedAt() != null) throw new BusinessException(409, "已有到岗记录的排班不能删除，请通过离场操作结束值班");
        boolean occupied = bookings.findBySlotDateAndSlotId(s.getSlotDate(), s.getSlotId()).stream()
                .anyMatch(b -> Set.of(Booking.BookingStatus.BOOKED, Booking.BookingStatus.CHECKED_IN,
                        Booking.BookingStatus.IN_USE).contains(b.getStatus()));
        boolean other = schedules.findBySlotDateAndSlotId(s.getSlotDate(), s.getSlotId()).stream()
                .anyMatch(a -> !a.getId().equals(id) && a.getAdmin().getRole() != User.Role.student);
        if (occupied && !other) throw new BusinessException(409, "场地已有预约，不能移除最后一位排班教员");
        schedules.delete(s);
        audit(actor, "delete_schedule", "schedule=" + id);
        push.pushDate(s.getSlotDate());
    }

    public ScheduleView arrive(User actor, Long id) {
        venue.lock();
        AdminSchedule s = schedules.findById(id).orElseThrow(() -> new BusinessException(404, "排班不存在"));
        if (!s.getAdmin().getId().equals(actor.getId())) throw new BusinessException(403, "到岗必须由值班教员本人确认");
        LocalDateTime now = LocalDateTime.now(clock);
        if (!arrivalWindow(s, now)) throw new BusinessException(409, "仅可在当天值班开始前30分钟至结束前确认到岗");
        if (s.getArrivedAt() != null && s.getDepartedAt() == null) return view(s, actor.getId());
        s.setArrivedAt(now);
        s.setDepartedAt(null);
        audit(actor, "coach_arrive", "schedule=" + id);
        push.pushDate(s.getSlotDate());
        return view(s, actor.getId());
    }

    /** Departure means physically leaving the room: close ALL open shifts of this coach. */
    public void depart(User actor) {
        venue.lock();
        List<AdminSchedule> own = schedules.findByAdminIdAndArrivedAtIsNotNullAndDepartedAtIsNull(actor.getId());
        if (own.isEmpty()) throw new BusinessException(409, "当前没有待结束的到岗记录");
        LocalDateTime now = LocalDateTime.now(clock);
        List<Long> relief = schedules.findBySlotDate(now.toLocalDate().toString()).stream()
                .filter(s -> !s.getAdmin().getId().equals(actor.getId()) && isCovering(s, now))
                .map(s -> s.getAdmin().getId()).distinct().toList();
        if (sessions.countByEndedAtIsNull() > 0 && relief.isEmpty())
            throw new BusinessException(409, "仍有训练进行中，请等待其他教员到岗交接或结束全部训练后再离场");
        own.forEach(s -> s.setDepartedAt(now));
        audit(actor, "coach_depart", "schedules=" + own.stream().map(AdminSchedule::getId).toList() + ";relief=" + relief);
        own.stream().map(AdminSchedule::getSlotDate).distinct().forEach(push::pushDate);
    }

    /** Caller holds the venue lock until the training transaction commits. */
    public void requirePresent(String date, String slotId) {
        LocalDateTime now = LocalDateTime.now(clock);
        if (schedules.findBySlotDateAndSlotId(date, slotId).stream().noneMatch(s -> isCovering(s, now)))
            throw new BusinessException(409, "本时段尚无教员到岗，请等待教员到场后再开始训练");
    }

    private void audit(User actor, String action, String detail) {
        audits.save(AuditLog.builder().admin(actor).action(action).targetUserId(actor.getId())
                .detail(detail).createdAt(LocalDateTime.now(clock)).build());
    }
}
