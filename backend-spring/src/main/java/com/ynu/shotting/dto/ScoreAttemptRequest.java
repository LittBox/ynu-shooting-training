package com.ynu.shoting.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
@Data
public class ScoreAttemptRequest {
    @NotBlank @Size(max=64) private String requestKey;
    @NotBlank @Pattern(regexp="final_|qualifying", message="请选择决赛或资格赛") private String mode;
    @NotEmpty @Size(min=3,max=6) private List<Double> groupTotals;
}
