package com.ynu.shoting.repository;

import com.ynu.shoting.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Booking e where e.id = :id")
    java.util.Optional<Booking> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);


    @Query("select b.device.id from Booking b where b.id = :id")
    Optional<Long> findDeviceId(@Param("id") Long id);

    @Query("select b.id from Booking b where b.status = 'BOOKED' and b.slotDate <= :today")
    List<Long> findOverdueCandidates(@Param("today") String today);

    List<Booking> findByUserId(Long userId);

    List<Booking> findBySlotDate(String slotDate);

    List<Booking> findBySlotDateAndSlotId(String slotDate, String slotId);

    Optional<Booking> findBySlotDateAndSlotIdAndDeviceIdAndStatusIn(
            String slotDate, String slotId, Long deviceId, List<Booking.BookingStatus> statuses);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId AND b.slotDate = :slotDate AND b.status NOT IN ('CANCELLED','NO_SHOW')")
    List<Booking> findActiveByUserAndDate(@Param("userId") Long userId, @Param("slotDate") String slotDate);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId AND b.status NOT IN ('CANCELLED','NO_SHOW')")
    List<Booking> findActiveByUser(@Param("userId") Long userId);

    /**
     * 计算 [weekStart, weekEnd] 内某用户的活跃预约数（用于每周上限校验）
     * weekStart / weekEnd 格式 YYYY-MM-DD（含两端）
     */
    @Query("SELECT COUNT(b) FROM Booking b WHERE b.user.id = :userId " +
           "AND b.slotDate >= :weekStart AND b.slotDate <= :weekEnd " +
           "AND b.status NOT IN ('CANCELLED','NO_SHOW')")
    long countActiveByUserInRange(@Param("userId") Long userId,
                                   @Param("weekStart") String weekStart,
                                   @Param("weekEnd") String weekEnd);
}
