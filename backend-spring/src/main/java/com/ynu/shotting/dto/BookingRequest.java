package com.ynu.shoting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BookingRequest {

    @NotNull(message = "设备ID不能为空")
    private Long deviceId;

    private boolean availabilityConfirmed;

    @NotBlank(message = "预约日期不能为空")
    private String slotDate;  // YYYY-MM-DD

    @NotBlank(message = "时间片不能为空")
    private String slotId;  // S1~S6
}
