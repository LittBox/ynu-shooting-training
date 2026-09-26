package com.ynu.shoting.repository;

import com.ynu.shoting.entity.NoShowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NoShowRecordRepository extends JpaRepository<NoShowRecord, Long> {

    List<NoShowRecord> findByUserId(Long userId);
}
