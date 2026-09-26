package com.ynu.shoting.repository;

import com.ynu.shoting.entity.UserAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserAvailabilityRepository extends JpaRepository<UserAvailability, Long> {

    boolean existsByUserIdAndSlotDateAndSlotId(Long userId, String date, String slot);
    void deleteByUserIdAndSlotDate(Long userId, String date);

    List<UserAvailability> findByUserId(Long userId);

    List<UserAvailability> findBySlotDate(String slotDate);
}
