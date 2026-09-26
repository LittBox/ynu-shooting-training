package com.ynu.shoting.controller;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.exception.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.AuthContext;
import com.ynu.shoting.service.*;
import com.ynu.shoting.config.Slot;
import com.ynu.shoting.websocket.SlotPushService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor @Transactional
public class AdminController {
    private final DeviceRepository devices;
    private final BookingRepository bookings;
    private final VenueDutyService duty;
    private final NoShowRecordRepository noShows;
    private final CancellationLogRepository cancellations;
    private final UserRepository users;
    private final AuditLogRepository audits;
    private final AuthContext auth;
    private final BookingAccess access;
    private final BookingService bookingService;
    private final SlotPushService push;
    private final Clock clock;
    public record BookingView(Long id,Long userId,Long deviceId,String slotDate,String slotId,String status) {
        static BookingView from(Booking b) { return new BookingView(b.getId(),b.getUser().getId(),b.getDevice().getId(),b.getSlotDate(),b.getSlotId(),b.getStatus().name()); }
    }
    @GetMapping("/devices") public ApiResponse<?> devices(HttpServletRequest req) { requireAdmin(req); return ApiResponse.ok(devices.findAll()); }
    @PostMapping("/devices") public ApiResponse<?> createDevice(HttpServletRequest req,@RequestParam String name,@RequestParam String type) {
        User admin=requireAdmin(req);
        if(name.isBlank() || name.length()>100) throw new BusinessException(400,"设备名称不能为空且不超过100字");
        Device d=devices.save(Device.builder().name(name).type(Device.DeviceType.valueOf(type)).build());
        audit(admin,"create_device",null,"device="+d.getId()); return ApiResponse.ok(d);
    }
    @PatchMapping("/devices/{id}/status") public ApiResponse<?> deviceStatus(HttpServletRequest req,@PathVariable Long id,@RequestParam String status) {
        User admin=requireAdmin(req);
        Device d=devices.findLockedById(id).orElseThrow(() -> new BusinessException(404,"设备不存在"));
        var next=Device.DeviceStatus.valueOf(status);
        if(next==Device.DeviceStatus.IN_USE || next==Device.DeviceStatus.BOOKED || d.getStatus()==Device.DeviceStatus.IN_USE)
            throw new BusinessException(409,"使用中状态由训练流程管理，请先结束训练");
        d.setStatus(next); audit(admin,"device_status",null,"device="+id+";status="+status);
        for(int i=0;i<=7;i++) push.pushDate(LocalDate.now(clock).plusDays(i).toString());
        return ApiResponse.ok(d);
    }
    @PostMapping("/schedules") public ApiResponse<?> schedule(HttpServletRequest req,@RequestParam Long adminId,
            @RequestParam String slotDate,@RequestParam String slotId) {
        return ApiResponse.ok(duty.schedule(requireAdmin(req),adminId,slotDate,slotId));
    }
    @GetMapping("/schedules") public ApiResponse<?> schedules(HttpServletRequest req,@RequestParam String date) {
        User actor=requireAdmin(req); return ApiResponse.ok(duty.list(date,actor.getId()));
    }
    @DeleteMapping("/schedules/{id}") public ApiResponse<?> deleteSchedule(HttpServletRequest req,@PathVariable Long id) {
        duty.delete(requireAdmin(req),id); return ApiResponse.ok();
    }
    @GetMapping("/duty") public ApiResponse<?> duty(HttpServletRequest req,@RequestParam String date) {
        User actor=requireAdmin(req); return ApiResponse.ok(duty.status(date,actor.getId()));
    }
    @PostMapping("/schedules/{id}/arrive") public ApiResponse<?> arrive(HttpServletRequest req,@PathVariable Long id) {
        return ApiResponse.ok(duty.arrive(requireAdmin(req),id));
    }
    @PostMapping("/duty/depart") public ApiResponse<?> depart(HttpServletRequest req) {
        duty.depart(requireAdmin(req)); return ApiResponse.ok();
    }
    @GetMapping("/bookings") public ApiResponse<?> bookings(HttpServletRequest req,@RequestParam(required=false) String date) {
        User admin=requireAdmin(req); audit(admin,"read_bookings",null,date);
        return ApiResponse.ok((date==null?bookings.findAll():bookings.findBySlotDate(date)).stream().map(BookingView::from).toList());
    }
    @PostMapping("/bookings/{id}/cancel") public ApiResponse<?> cancel(HttpServletRequest req,@PathVariable Long id,@RequestParam(required=false) String reason) {
        User admin=requireAdmin(req); bookingService.cancel(req,id); audit(admin,"admin_cancel",null,"booking="+id); return ApiResponse.ok();
    }
    @PostMapping("/bookings/{id}/no-show") public ApiResponse<?> noShow(HttpServletRequest req,@PathVariable Long id) {
        User admin=requireAdmin(req); Booking b=access.lock(id);
        LocalDateTime deadline=LocalDateTime.of(LocalDate.parse(b.getSlotDate()),Slot.fromId(b.getSlotId()).getStart()).plusMinutes(10);
        if(b.getStatus()!=Booking.BookingStatus.BOOKED || !LocalDateTime.now(clock).isAfter(deadline)) throw new BusinessException(409,"仅超时未签到的预约可标记爽约");
        b.setStatus(Booking.BookingStatus.NO_SHOW);
        noShows.save(NoShowRecord.builder().booking(b).user(b.getUser()).createdAt(LocalDateTime.now(clock)).build());
        audit(admin,"mark_no_show",b.getUser().getId(),"booking="+id); push.pushDate(b.getSlotDate()); return ApiResponse.ok();
    }
    @GetMapping("/no-shows") public ApiResponse<?> noShows(HttpServletRequest req,@RequestParam(required=false) Long userId) {
        User admin=requireAdmin(req); audit(admin,"read_no_shows",userId,null);
        return ApiResponse.ok((userId==null?noShows.findAll():noShows.findByUserId(userId)).stream().map(r -> Map.of("id",r.getId(),"bookingId",r.getBooking().getId(),"userId",r.getUser().getId(),"createdAt",r.getCreatedAt())).toList());
    }
    @GetMapping("/cancellations") public ApiResponse<?> cancellations(HttpServletRequest req) {
        User admin=requireAdmin(req); audit(admin,"read_cancellations",null,null);
        return ApiResponse.ok(cancellations.findAll().stream().map(c -> Map.of("id",c.getId(),"bookingId",c.getBooking().getId(),"userId",c.getUser().getId(),"level",c.getLevel(),"reason",c.getReason(),"createdAt",c.getCreatedAt())).toList());
    }
    @GetMapping("/audit") public ApiResponse<?> audits(HttpServletRequest req,@RequestParam(required=false) Long userId) {
        User admin=requireAdmin(req); audit(admin,"read_audit",userId,null);
        return ApiResponse.ok((userId==null?audits.findAll():audits.findByTargetUserIdOrderByCreatedAtDesc(userId)).stream().map(a -> Map.of("id",a.getId(),"action",a.getAction(),"detail",Objects.toString(a.getDetail(),""),"createdAt",a.getCreatedAt())).toList());
    }
    private User requireAdmin(HttpServletRequest req) { User user=auth.currentUser(req); if(user.getRole()==User.Role.student) throw new BusinessException(403,"权限不足"); return user; }
    private void audit(User admin,String action,Long userId,String detail) { audits.save(AuditLog.builder().admin(admin).action(action).targetUserId(userId).detail(detail).createdAt(LocalDateTime.now(clock)).build()); }
}
