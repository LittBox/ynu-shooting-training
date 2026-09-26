package com.ynu.shoting.dto;
import com.ynu.shoting.entity.TrainingSession;
import java.time.LocalDateTime;
import java.util.List;
public record TrainingVO(Long id, Long bookingId, String deviceName, String mode,
        LocalDateTime startedAt, LocalDateTime endedAt, Integer actualDurationMin, Double finalScore, Boolean personalBest,
        List<ModeScore> finalScores) {
    public record ModeScore(String mode,double finalScore,boolean personalBest) {}
    public static TrainingVO finished(TrainingSession s, List<ModeScore> scores) {
        ModeScore single=scores.size()==1?scores.getFirst():null;
        return new TrainingVO(s.getId(),s.getBooking().getId(),s.getDevice().getName(),s.getMode().name(),s.getStartedAt(),s.getEndedAt(),s.getActualDurationMin(),
                single==null?null:single.finalScore(),single==null?null:single.personalBest(),scores);
    }
    public static TrainingVO from(TrainingSession s) {
        return new TrainingVO(s.getId(), s.getBooking().getId(), s.getDevice().getName(), s.getMode().name(),
            s.getStartedAt(), s.getEndedAt(), s.getActualDurationMin(), null, null,List.of());
    }
}
