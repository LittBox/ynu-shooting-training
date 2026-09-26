package com.ynu.shoting.repository;

import com.ynu.shoting.entity.CancellationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CancellationLogRepository extends JpaRepository<CancellationLog, Long> {
}
