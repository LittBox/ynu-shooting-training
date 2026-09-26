package com.ynu.shoting.controller;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/leaderboard") @RequiredArgsConstructor
public class LeaderboardController {
    private final LeaderboardService service;
    @GetMapping public ApiResponse<?> leaderboard(@RequestParam String weapon, @RequestParam String event,
            @RequestParam(defaultValue="BEST") String metric, @RequestParam(defaultValue="ALL") String gender,
            @RequestParam(defaultValue="20") int limit) {
        return ApiResponse.ok(service.rank(weapon,event,metric,gender,limit));
    }
}
