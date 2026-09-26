package com.ynu.shoting.repository;
import com.ynu.shoting.entity.ScoreAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScoreAttemptRepository extends JpaRepository<ScoreAttempt,Long> {
    List<ScoreAttempt> findBySessionIdIn(Collection<Long> sessionIds);
    List<ScoreAttempt> findBySessionIdOrderByRecordedAtAscIdAsc(Long sessionId);
    Optional<ScoreAttempt> findBySessionIdAndRequestKey(Long sessionId,String requestKey);
}
