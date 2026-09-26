package com.ynu.shoting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ProfileRequest {

    @NotBlank(message = "真实姓名不能为空")
    @jakarta.validation.constraints.Size(max=40)
    private String realName;

    @NotBlank(message = "学号不能为空")
    @jakarta.validation.constraints.Size(max=32)
    private String studentNo;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
    @NotBlank(message="请选择性别")
    @Pattern(regexp="M|F", message="请选择男或女")
    private String gender;
}
