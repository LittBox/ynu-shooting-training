package com.ynu.shoting.service;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.BookingRepository;
import com.ynu.shoting.config.Slot;
import com.ynu.shoting.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.List;
@Component @RequiredArgsConstructor
public class TrainingResumePolicy {
    private final BookingRepository bookings;
    private final VenueDutyService duty;
    private final Clock clock;
    public String reason(TrainingSession session) {
        if(session.isHistorical())return "历史补录训练不能恢复，请预约新的训练";
        Booking b=session.getBooking();
        if(b.getStatus()!=Booking.BookingStatus.COMPLETED || session.getEndedAt()==null)return "仅已结束的训练可以继续";
        LocalDate date=LocalDate.parse(b.getSlotDate());
        if(!date.equals(LocalDate.now(clock)) || !LocalDateTime.now(clock).isBefore(date.atTime(Slot.fromId(b.getSlotId()).getEnd())))
            return "原时段已结束，可补登成绩；再次训练请选择当前空闲时段";
        Device device=session.getDevice();
        if(device.getStatus()!=Device.DeviceStatus.IDLE && device.getStatus()!=Device.DeviceStatus.BOOKED)
            return "设备正在使用或不可用，暂不能继续训练";
        if(bookings.findBySlotDateAndSlotIdAndDeviceIdAndStatusIn(b.getSlotDate(),b.getSlotId(),device.getId(),
                List.of(Booking.BookingStatus.BOOKED,Booking.BookingStatus.CHECKED_IN,Booking.BookingStatus.IN_USE)).isPresent())
            return "该设备已有其他有效预约或训练，暂不能继续";
        if(bookings.findByUserId(session.getUser().getId()).stream().anyMatch(other->other.getStatus()==Booking.BookingStatus.IN_USE))
            return "请先结束本人另一场正在进行的训练";
        try { duty.requirePresent(b.getSlotDate(),b.getSlotId()); }
        catch(BusinessException e) { return e.getMessage(); }
        return null;
    }
}
