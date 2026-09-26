package com.ynu.shoting.controller;

import com.ynu.shoting.dto.*;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.AuthService;
import com.ynu.shoting.service.CoachInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CoachInvitationService coachInvitations;

    public record CoachActivationRequest(
            @jakarta.validation.constraints.NotBlank(message="请输入教练邀请码")
            @jakarta.validation.constraints.Size(max=128) String inviteCode) {}

    @PostMapping("/coach-activation")
    public ApiResponse<AuthUserVO> activateCoach(HttpServletRequest request,
            @Valid @RequestBody CoachActivationRequest body) {
        return ApiResponse.ok(coachInvitations.activate(authService.me(request).getId(), body.inviteCode()));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/profile")
    public ApiResponse<AuthUserVO> submitProfile(HttpServletRequest request,
                                          @Valid @RequestBody ProfileRequest req) {
        return ApiResponse.ok(AuthUserVO.from(authService.submitProfile(request, req)));
    }

    @GetMapping("/me")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiResponse<AuthUserVO> me(HttpServletRequest request) {
        return ApiResponse.ok(AuthUserVO.from(authService.me(request)));
    }
}
