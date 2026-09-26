package com.ynu.shoting.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import java.time.LocalTime;

/**
 * 标准时间片（constitution 第三条）
 * S1 08:30-10:10
 * S2 10:30-12:10
 * S3 14:00-15:40
 * S4 16:00-17:40
 * S5 19:00-20:40
 * S6 21:30-22:30
 */
@Getter
@AllArgsConstructor
public enum Slot {

    S1("S1", "上午1", LocalTime.of(8, 30), LocalTime.of(10, 10)),
    S2("S2", "上午2", LocalTime.of(10, 30), LocalTime.of(12, 10)),
    S3("S3", "下午1", LocalTime.of(14, 0), LocalTime.of(15, 40)),
    S4("S4", "下午2", LocalTime.of(16, 0), LocalTime.of(17, 40)),
    S5("S5", "晚间1", LocalTime.of(19, 0), LocalTime.of(20, 40)),
    S6("S6", "晚间2", LocalTime.of(21, 30), LocalTime.of(22, 30));

    private final String id;
    private final String label;
    private final LocalTime start;
    private final LocalTime end;

    public static Slot fromId(String id) {
        for (Slot s : values()) {
            if (s.id.equals(id)) return s;
        }
        throw new IllegalArgumentException("Unknown slot id: " + id);
    }
}
