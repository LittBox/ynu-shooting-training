package com.ynu.shoting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotVO {
    private String id;
    private String label;
    private String start;
    private String end;
    private Long deviceId;
    private String deviceName;
    private String deviceType;
    private String deviceStatus;
    private Boolean staffed;
    private Boolean coachPresent;
    private Integer available;  // 0 满 / 1 有空位
    private Boolean walkInAvailable; // 当前时段可直接训练
    private Integer bookedCount;
    private Integer capacity;
    private List<String> bookedBy;  // 占用者昵称列表
}
