package com.ynu.shoting.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "code 不能为空")
    @jakarta.validation.constraints.Size(max=256)
    private String code;

    private String mode = "wechat";

    private String nickname;
    private String avatarUrl;
}
