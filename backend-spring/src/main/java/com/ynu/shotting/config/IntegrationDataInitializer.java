package com.ynu.shoting.config;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
/** Explicit, loopback-only ephemeral integration fixtures; never active by default. */
@Component @Profile("integration") @Order(1) @RequiredArgsConstructor
public class IntegrationDataInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final AdminScheduleRepository schedules;
    private final Clock clock;
    @Override @Transactional public void run(ApplicationArguments args) {
        User admin = users.findByOpenid("mock:integration-admin").orElseGet(() -> users.save(User.builder()
            .openid("mock:integration-admin").nickname("联调管理员").role(User.Role.admin).build()));
        for (int i=0; i<=7; i++) for (Slot slot : Slot.values()) {
            String date=LocalDate.now(clock).plusDays(i).toString();
            if (!schedules.existsBySlotDateAndSlotIdAndAdminRoleNot(date, slot.getId(), User.Role.student))
                schedules.save(AdminSchedule.builder().admin(admin).slotDate(date).slotId(slot.getId()).build());
        }
    }
}
