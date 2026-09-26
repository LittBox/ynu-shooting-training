package com.ynu.shoting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingVO {
    private Long id;
    private Long sessionId;
    private String slotDate;
    private String slotId;
    private String slotLabel;
    private String slotStart;
    private String slotEnd;
    private String deviceName;
    private String deviceType;
    private String status;
    private String nickname;
    private String realName;
    private String studentNo;
    private LocalDateTime bookedAt;
    private LocalDateTime checkedInAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
