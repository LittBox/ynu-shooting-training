package com.ynu.shoting.controller;

import com.ynu.shoting.dto.*;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ApiResponse<BookingVO> create(HttpServletRequest request,
                                        @Valid @RequestBody BookingRequest req) {
        return ApiResponse.ok(bookingService.create(request, req));
    }

    @PostMapping("/walk-in")
    public ApiResponse<BookingVO> walkIn(HttpServletRequest request, @Valid @RequestBody WalkInRequest req) {
        return ApiResponse.ok(bookingService.walkIn(request, req));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<Void> cancel(HttpServletRequest request,
                                   @PathVariable Long id) {
        bookingService.cancel(request, id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/checkin")
    public ApiResponse<BookingVO> checkIn(HttpServletRequest request,
                                          @PathVariable Long id) {
        return ApiResponse.ok(bookingService.checkIn(request, id));
    }

    @GetMapping("/me")
    public ApiResponse<List<BookingVO>> myBookings(HttpServletRequest request) {
        return ApiResponse.ok(bookingService.myBookings(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingVO> detail(HttpServletRequest request,
                                         @PathVariable Long id) {
        return ApiResponse.ok(bookingService.detail(request, id));
    }

    @GetMapping("/slots")
    public ApiResponse<List<SlotVO>> dailySlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String deviceType) {
        return ApiResponse.ok(bookingService.dailySlots(date, deviceType));
    }
}
