package com.ynu.shoting.service;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.config.*;
import com.ynu.shoting.websocket.SlotPushService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service @RequiredArgsConstructor
public class NoShowService {
    private final BookingAccess access;
    private final BookingRepository bookings;
    private final NoShowRecordRepository records;
    private final BookingProperties props;
    private final Clock clock;
    private final SlotPushService push;
    @Transactional
    public void expire(Long id) {
        Booking b = access.lock(id);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime deadline = LocalDateTime.of(LocalDate.parse(b.getSlotDate()), Slot.fromId(b.getSlotId()).getStart()).plusMinutes(props.getCheckinTimeoutMin());
        if (b.getStatus() != Booking.BookingStatus.BOOKED || !now.isAfter(deadline)) return;
        b.setStatus(Booking.BookingStatus.NO_SHOW);
        bookings.save(b);
        records.save(NoShowRecord.builder().user(b.getUser()).booking(b).createdAt(now).build());
        push.pushDate(b.getSlotDate());
    }
}
