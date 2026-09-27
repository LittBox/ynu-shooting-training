package com.ynu.shoting.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;

public record HistoricalTrainingRequest(
        @NotBlank @Pattern(regexp="[a-zA-Z0-9-]{16,64}") String requestKey,
        @NotNull @Positive Long userId,
        @NotBlank @Pattern(regexp="pistol|rifle") String weapon,
        @NotNull LocalDateTime startedAt,
        @NotNull LocalDateTime endedAt,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Round> rounds) {
    public record Round(@NotBlank @Pattern(regexp="final_|qualifying") String mode,
                        @NotEmpty @Size(min=3,max=6) List<Double> groupTotals) {}
}
