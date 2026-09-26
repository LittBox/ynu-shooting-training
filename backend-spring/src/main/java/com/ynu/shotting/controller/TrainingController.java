package com.ynu.shoting.controller;

import com.ynu.shoting.dto.ScoreRequest;
import com.ynu.shoting.dto.TrainingVO;
import com.ynu.shoting.dto.ScoreVO;
import com.ynu.shoting.entity.Score;
import com.ynu.shoting.entity.TrainingSession;
import com.ynu.shoting.exception.ApiResponse;
import com.ynu.shoting.service.TrainingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/training")
@RequiredArgsConstructor
public class TrainingController {

    private final TrainingService trainingService;
    private final com.ynu.shoting.service.TrainingResultsService results;

    @PostMapping("/{id}/attempts")
    public ApiResponse<com.ynu.shoting.service.TrainingResultsService.AttemptView> attempt(HttpServletRequest request,
            @PathVariable Long id, @Valid @RequestBody com.ynu.shoting.dto.ScoreAttemptRequest body) {
        return ApiResponse.ok(results.register(request,id,body));
    }
    @GetMapping("/{id}/results")
    public ApiResponse<com.ynu.shoting.service.TrainingResultsService.Results> results(HttpServletRequest request,@PathVariable Long id) {
        return ApiResponse.ok(results.detail(request,id));
    }
    @GetMapping("/records/me")
    public ApiResponse<com.ynu.shoting.service.TrainingResultsService.History> history(HttpServletRequest request) {
        return ApiResponse.ok(results.history(request));
    }

    @PostMapping("/start/{bookingId}")
    public ApiResponse<TrainingVO> start(HttpServletRequest request,
                                              @PathVariable Long bookingId,
                                              @RequestParam(defaultValue="final_") String mode) {
        return ApiResponse.ok(trainingService.start(request, bookingId, mode));
    }

    @PostMapping("/resume/{sessionId}")
    public ApiResponse<TrainingVO> resume(HttpServletRequest request,@PathVariable Long sessionId) {
        return ApiResponse.ok(trainingService.resume(request,sessionId));
    }

    @PostMapping("/finish/{sessionId}")
    public ApiResponse<TrainingVO> finish(HttpServletRequest request,
                                              @PathVariable Long sessionId) {
        return ApiResponse.ok(trainingService.finish(request, sessionId));
    }

    @PostMapping("/score")
    public ApiResponse<ScoreVO> submitScore(HttpServletRequest request,
                                        @Valid @RequestBody ScoreRequest req) {
        return ApiResponse.ok(trainingService.submitScore(request, req));
    }

    @GetMapping("/me")
    public ApiResponse<List<TrainingVO>> mySessions(HttpServletRequest request) {
        return ApiResponse.ok(trainingService.mySessions(request));
    }
}
