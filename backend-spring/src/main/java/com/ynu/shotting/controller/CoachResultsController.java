package com.ynu.shoting.controller;

import com.ynu.shoting.dto.ScoreCorrectionRequest;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.CoachResultsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/coach/training") @RequiredArgsConstructor
public class CoachResultsController {
    private final CoachResultsService results;
    @GetMapping("/days")
    public ApiResponse<CoachResultsService.Days> days(HttpServletRequest request,@RequestParam(defaultValue="0") int page) {
        return ApiResponse.ok(results.days(request,page));
    }
    @GetMapping("/days/{date}")
    public ApiResponse<CoachResultsService.DayResults> day(HttpServletRequest request,
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(iso=org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate date) {
        return ApiResponse.ok(results.day(request,date));
    }
    @GetMapping
    public ApiResponse<CoachResultsService.Records> list(HttpServletRequest request,@RequestParam(defaultValue="") String search,@RequestParam(defaultValue="0") int page) {
        return ApiResponse.ok(results.list(request,search,page));
    }
    @GetMapping("/{id}")
    public ApiResponse<CoachResultsService.Detail> detail(HttpServletRequest request,@PathVariable Long id) {
        return ApiResponse.ok(results.detail(request,id));
    }
    @PutMapping("/{id}/attempts/{recordId}")
    public ApiResponse<CoachResultsService.Detail> correct(HttpServletRequest request,@PathVariable Long id,@PathVariable Long recordId,@Valid @RequestBody ScoreCorrectionRequest body) {
        return ApiResponse.ok(results.correct(request,id,recordId,false,body));
    }
    @PutMapping("/{id}/legacy-scores/{recordId}")
    public ApiResponse<CoachResultsService.Detail> correctLegacy(HttpServletRequest request,@PathVariable Long id,@PathVariable Long recordId,@Valid @RequestBody ScoreCorrectionRequest body) {
        return ApiResponse.ok(results.correct(request,id,recordId,true,body));
    }
}
