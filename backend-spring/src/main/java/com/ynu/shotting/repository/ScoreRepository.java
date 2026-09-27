package com.ynu.shoting.repository;

import com.ynu.shoting.entity.Score;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ScoreRepository extends JpaRepository<Score, Long> {
    java.util.List<Score> findBySessionIdIn(java.util.Collection<Long> sessionIds);

    @org.springframework.data.jpa.repository.Query("select s from Score s join fetch s.session t join fetch t.user u left join fetch u.profile left join fetch t.device d left join t.booking b where s.mode = :mode and coalesce(t.historicalWeapon,d.type) = :weapon and (t.historicalWeapon is not null or b.status in ('COMPLETED','IN_USE'))")
    java.util.List<Score> findPublished(@org.springframework.data.repository.query.Param("mode") Score.ScoreMode mode,
        @org.springframework.data.repository.query.Param("weapon") com.ynu.shoting.entity.Device.DeviceType weapon);

    @org.springframework.data.jpa.repository.Query("select s from Score s join fetch s.session t left join fetch t.device left join t.booking b where t.user.id = :userId and (t.historicalWeapon is not null or b.status in ('COMPLETED','IN_USE'))")
    java.util.List<Score> findPublishedByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);

    Optional<Score> findBySessionIdAndMode(Long sessionId, Score.ScoreMode mode);
    java.util.List<Score> findBySessionIdOrderByModeAsc(Long sessionId);
}
