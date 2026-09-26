package com.ynu.shoting.service;

import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Single classroom mutex shared by scheduling, attendance and training transitions. */
@Component
@RequiredArgsConstructor
public class VenueAccess {
    private final DeviceRepository devices;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock() {
        // Devices cannot be deleted through the app. Lock their stable rows in ID order,
        // before user/booking/session rows, so concurrent leave/start cannot both succeed.
        if (devices.findAllForVenueLock().isEmpty())
            throw new BusinessException(409, "场地尚未配置设备，请联系管理员");
    }
}
