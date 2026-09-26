package com.ynu.shoting.controller;

import com.ynu.shoting.dto.SlotDetailVO;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.SlotDetailService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/bookings/slots")
@RequiredArgsConstructor
public class SlotDetailController {
    private final SlotDetailService service;

    @GetMapping("/details")
    public ApiResponse<SlotDetailVO> detail(HttpServletRequest request,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String slotId) {
        return ApiResponse.ok(service.detail(request, date, slotId));
    }
}
