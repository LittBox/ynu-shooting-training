package com.ynu.shoting.scheduler;
import com.ynu.shoting.repository.BookingRepository;
import com.ynu.shoting.service.NoShowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.*;
@Slf4j @Component @RequiredArgsConstructor
@ConditionalOnProperty(name="booking.sweeper-enabled", havingValue="true", matchIfMissing=true)
public class BookingNoShowScheduler {
    private final BookingRepository bookings;
    private final NoShowService noShows;
    private final Clock clock;
    @Scheduled(fixedDelay=60000, initialDelay=30000)
    public void sweep() {
        for (Long id : bookings.findOverdueCandidates(LocalDate.now(clock).toString())) {
            try { noShows.expire(id); }
            catch (Exception ex) { log.error("No-show transition failed for booking {}", id, ex); }
        }
    }
}
