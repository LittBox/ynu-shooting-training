package com.ynu.shoting.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public record ScoreCorrectionRequest(
        @NotBlank @Size(max=64) String expectedState,
        @NotBlank @Size(max=200) String reason,
        List<Double> groupTotals,
        Double totalScore) {}
