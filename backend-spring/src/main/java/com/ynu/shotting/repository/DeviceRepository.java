package com.ynu.shoting.repository;

import com.ynu.shoting.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from Device d order by d.id")
    List<Device> findAllForVenueLock();

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Device e where e.id = :id")
    java.util.Optional<Device> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);


    List<Device> findByType(Device.DeviceType type);

    List<Device> findByStatus(Device.DeviceStatus status);
}
