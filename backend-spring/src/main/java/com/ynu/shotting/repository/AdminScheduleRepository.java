package com.ynu.shoting.repository;

import com.ynu.shoting.entity.AdminSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AdminScheduleRepository extends JpaRepository<AdminSchedule, Long> {

    boolean existsBySlotDateAndSlotIdAndAdminRoleNot(String date, String slot, com.ynu.shoting.entity.User.Role role);

    java.util.Optional<AdminSchedule> findByAdminIdAndSlotDateAndSlotId(Long adminId, String date, String slotId);

    List<AdminSchedule> findBySlotDateAndSlotId(String date, String slotId);

    List<AdminSchedule> findByAdminIdAndArrivedAtIsNotNullAndDepartedAtIsNull(Long adminId);

    List<AdminSchedule> findBySlotDate(String slotDate);

    List<AdminSchedule> findByAdminId(Long adminId);
}
