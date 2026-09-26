package com.ynu.shoting.service;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class BookingAccess {
    private final BookingRepository bookings;
    private final DeviceRepository devices;
    // All transitions acquire the same resource order: device, then booking.
    // Read only the id first so a stale managed Booking is not cached before the lock.
    public Booking lock(Long id) {
        Long deviceId = bookings.findDeviceId(id).orElseThrow(() -> new BusinessException(404, "预约不存在"));
        devices.findLockedById(deviceId).orElseThrow(() -> new BusinessException(404, "设备不存在"));
        return bookings.findLockedById(id).orElseThrow(() -> new BusinessException(404, "预约不存在"));
    }
    public void requireOwner(User user, Booking booking) {
        if (!booking.getUser().getId().equals(user.getId()) && user.getRole() == User.Role.student)
            throw new BusinessException(403, "无权操作此预约");
    }
}
