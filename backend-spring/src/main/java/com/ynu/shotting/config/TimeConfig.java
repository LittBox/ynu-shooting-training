package com.ynu.shoting.config;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.time.*;
import java.util.Arrays;
@Configuration
public class TimeConfig {
    @Bean public Clock venueClock(Environment environment) {
        ZoneId zone=ZoneId.of("Asia/Shanghai");
        String fixed=environment.getProperty("integration.clock", "");
        if (!fixed.isBlank() && Arrays.asList(environment.getActiveProfiles()).contains("integration"))
            return Clock.fixed(Instant.parse(fixed),zone);
        return Clock.system(zone);
    }
}
