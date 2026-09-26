package com.ynu.shoting.config;
import com.ynu.shoting.entity.Device;
import com.ynu.shoting.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @Order(0) @RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final DeviceRepository devices;
    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (devices.count() != 0) return;
        devices.save(Device.builder().name("手枪靶位 1").type(Device.DeviceType.pistol).build());
        devices.save(Device.builder().name("步枪靶位 1").type(Device.DeviceType.rifle).build());
    }
}
