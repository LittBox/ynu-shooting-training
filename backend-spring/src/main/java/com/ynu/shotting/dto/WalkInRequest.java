package com.ynu.shoting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class WalkInRequest extends BookingRequest {
    @NotBlank @Pattern(regexp="final_|qualifying", message="请选择训练模式")
    private String mode;
}
