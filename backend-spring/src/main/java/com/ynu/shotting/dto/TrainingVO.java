package com.ynu.shoting.dto;
import com.ynu.shoting.entity.TrainingSession;
import java.time.LocalDateTime;
import java.util.List;
public record TrainingVO(Long id, Long bookingId, String deviceName, String mode,
        LocalDateTime startedAt, LocalDateTime endedAt, Integer actualDurationMin, Double finalScore, Boolean personalBest,
        List<ModeScore> finalScores, boolean historical, String recordedByName, LocalDateTime recordedAt) {
    public record ModeScore(String mode,double finalScore,boolean personalBest) {}
    public static TrainingVO finished(TrainingSession s, List<ModeScore> scores) {
        ModeScore single=scores.size()==1?scores.getFirst():null;
        return new TrainingVO(s.getId(),s.getBooking()==null?null:s.getBooking().getId(),s.deviceLabel(),s.getMode().name(),s.getStartedAt(),s.getEndedAt(),s.getActualDurationMin(),
                single==null?null:single.finalScore(),single==null?null:single.personalBest(),scores,s.isHistorical(),recorder(s),s.getCreatedAt());
    }
    private static String recorder(TrainingSession s) {
        if(s.getRecordedBy()==null)return null;
        var p=s.getRecordedBy().getProfile();
        return p==null?s.getRecordedBy().getNickname():p.getRealName();
    }
    public static TrainingVO from(TrainingSession s) {
        return new TrainingVO(s.getId(), s.getBooking()==null?null:s.getBooking().getId(), s.deviceLabel(), s.getMode().name(),
            s.getStartedAt(), s.getEndedAt(), s.getActualDurationMin(), null, null,List.of(),s.isHistorical(),recorder(s),s.getCreatedAt());
    }
}
