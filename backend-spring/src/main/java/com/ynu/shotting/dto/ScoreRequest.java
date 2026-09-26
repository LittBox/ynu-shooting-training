package com.ynu.shoting.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;

@Data
public class ScoreRequest {

    @NotNull(message = "会话ID不能为空")
    private Long sessionId;

    /** 射击成绩列表，决赛 24 个，资格赛 60 个 */
    @NotEmpty(message = "射击成绩不能为空")
    private List<Double> shotScores;

    /** 每组发数（变长）。决赛传 [10, 10, 4]；资格赛传 [10, 10, 10, 10, 10, 10] */
    @NotEmpty(message = "每组发数不能为空")
    private List<Integer> groupSpec;

    private Integer xCount;
    /** final | qualifying */
    private String mode;
}
