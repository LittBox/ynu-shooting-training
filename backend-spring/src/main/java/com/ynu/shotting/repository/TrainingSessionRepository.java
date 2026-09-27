package com.ynu.shoting.repository;

import com.ynu.shoting.entity.TrainingSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {
    interface CoachDay {
        java.time.LocalDate getDate();
        long getMembers();
        long getSessions();
    }
    @org.springframework.data.jpa.repository.Query("select cast(s.startedAt as LocalDate) as date, count(distinct s.user.id) as members, count(s.id) as sessions from TrainingSession s left join s.booking b where (s.historicalWeapon is not null or b.status in ('COMPLETED','IN_USE')) and s.startedAt is not null group by cast(s.startedAt as LocalDate) order by cast(s.startedAt as LocalDate) desc")
    org.springframework.data.domain.Slice<CoachDay> findCoachDays(org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select s from TrainingSession s join fetch s.user u left join fetch u.profile left join fetch s.device left join fetch s.booking b where (s.historicalWeapon is not null or b.status in ('COMPLETED','IN_USE')) and s.startedAt >= :start and s.startedAt < :end order by s.startedAt, s.id")
    List<TrainingSession> findCoachDay(@org.springframework.data.repository.query.Param("start") java.time.LocalDateTime start,
                                      @org.springframework.data.repository.query.Param("end") java.time.LocalDateTime end);

    @org.springframework.data.jpa.repository.Query("select s from TrainingSession s join s.user u left join u.profile p left join s.booking b where (s.historicalWeapon is not null or b.status in ('COMPLETED','IN_USE')) and (:search = '' or lower(p.realName) like lower(concat('%', :search, '%')) or p.studentNo like concat('%', :search, '%'))")
    org.springframework.data.domain.Slice<TrainingSession> findForCoach(@org.springframework.data.repository.query.Param("search") String search, org.springframework.data.domain.Pageable pageable);

    Optional<TrainingSession> findByHistoryRequestKey(String key);

    long countByEndedAtIsNull();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from TrainingSession e where e.id = :id")
    java.util.Optional<TrainingSession> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);


    @org.springframework.data.jpa.repository.Query("select s.booking.id from TrainingSession s where s.id = :id")
    Optional<Long> findBookingId(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<TrainingSession> findByBookingId(Long bookingId);

    List<TrainingSession> findByUserId(Long userId);
}
