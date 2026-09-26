package com.ynu.shoting.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 业务配置：约束常量、时间片定义（来自宪法）
 */
@Component
@ConfigurationProperties(prefix = "booking")
@Getter
@Setter
public class BookingProperties {

    private int dailyLimit = 3;
    private int weeklyLimit = 10;
    private int advanceDays = 7;
    private int cancelFreeHours = 12;
    private int cancelLateHours = 1;
    private int checkinBeforeMin = 30;
    private int checkinTimeoutMin = 10;

    @Component
    @ConfigurationProperties(prefix = "slots")
    @Getter
@Setter
    public static class Slots {
        private List<String> values;
    }
}
