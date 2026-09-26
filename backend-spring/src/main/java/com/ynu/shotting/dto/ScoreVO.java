package com.ynu.shoting.dto;
import com.ynu.shoting.entity.Score;
import java.time.LocalDateTime;
public record ScoreVO(Long id, Long sessionId, String mode, Double totalScore, Integer shotCount,
        String groupScores, String groupSpec, LocalDateTime recordedAt) {
    public static ScoreVO from(Score s) {
        return new ScoreVO(s.getId(), s.getSession().getId(), s.getMode().name(), s.getTotalScore(),
            s.getShotCount(), s.getGroupScores(), s.getGroupSpec(), s.getRecordedAt());
    }
}
