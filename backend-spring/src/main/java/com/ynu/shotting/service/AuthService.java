package com.ynu.shoting.service;

import com.ynu.shoting.dto.LoginRequest;
import com.ynu.shoting.dto.LoginResponse;
import com.ynu.shoting.dto.ProfileRequest;
import com.ynu.shoting.entity.Profile;
import com.ynu.shoting.entity.User;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {

    /**
     * 微信登录：拿 code 换取 openid（如未对接微信则按 openid 直登测试），自动注册新用户
     */
    LoginResponse login(LoginRequest request);

    /**
     * 提交实名信息
     */
    User submitProfile(HttpServletRequest request, ProfileRequest req);

    /**
     * 当前用户信息
     */
    User me(HttpServletRequest request);
}
