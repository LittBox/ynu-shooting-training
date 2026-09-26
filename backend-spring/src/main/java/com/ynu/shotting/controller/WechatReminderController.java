package com.ynu.shoting.controller;

import com.ynu.shoting.entity.WechatReminder.Kind;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.WechatReminderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/reminders") @RequiredArgsConstructor
public class WechatReminderController {
    private final WechatReminderService reminders;
    public record Subscription(@NotBlank @Size(max=128) String templateId,@NotNull @AssertTrue Boolean accepted) {}
    @GetMapping("/{kind}/{id}")
    public ApiResponse<WechatReminderService.View> status(HttpServletRequest request,@PathVariable Kind kind,@PathVariable Long id) {
        return ApiResponse.ok(reminders.status(request,kind,id));
    }
    @PostMapping("/{kind}/{id}")
    public ApiResponse<WechatReminderService.View> subscribe(HttpServletRequest request,@PathVariable Kind kind,@PathVariable Long id,@Valid @RequestBody Subscription body) {
        return ApiResponse.ok(reminders.subscribe(request,kind,id,body.templateId(),body.accepted()));
    }
    @DeleteMapping("/{kind}/{id}")
    public ApiResponse<WechatReminderService.View> cancel(HttpServletRequest request,@PathVariable Kind kind,@PathVariable Long id) {
        return ApiResponse.ok(reminders.cancel(request,kind,id));
    }
}
