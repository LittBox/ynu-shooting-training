package com.ynu.shoting.repository;

import com.ynu.shoting.entity.WechatReminder;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface WechatReminderRepository extends JpaRepository<WechatReminder,Long> {
    Optional<WechatReminder> findByKindAndTargetId(WechatReminder.Kind kind,Long targetId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from WechatReminder r where r.id=:id")
    Optional<WechatReminder> findLockedById(@Param("id") Long id);
    @Query("select r.id from WechatReminder r where (r.status='PENDING' and r.nextAttemptAt<=:now) or (r.status='SENDING' and r.claimedAt<:stale) order by r.nextAttemptAt,r.id")
    List<Long> findDue(@Param("now") LocalDateTime now,@Param("stale") LocalDateTime stale,Pageable page);
}
