package com.ynu.shoting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.dto.*;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class TrainingResultsService {
    private final VenueAccess venue;
    private final TrainingResumePolicy resumePolicy;
    private final TrainingSessionRepository sessions;
    private final ScoreAttemptRepository attempts;
    private final ScoreRepository scores;
    private final AuthContext auth;
    private final ObjectMapper json;
    private final Clock clock;
    public record AttemptView(Long id,String mode,double totalScore,String groupTotals,LocalDateTime recordedAt) {}
    public record ModeResult(String mode,Double bestTotal,Double finalTotal) {}
    public record Results(TrainingVO session,List<AttemptView> attempts,List<ModeResult> modeResults,Double bestTotal,Double finalTotal,boolean canResume,String resumeUnavailableReason) {}
    public record HistoryRow(Long sessionId,String weapon,String mode,double totalScore,LocalDateTime recordedAt,boolean personalRecord) {}
    public record History(List<HistoryRow> personalBests,List<HistoryRow> sessions,List<TrainingVO> unscoredSessions) {}
    record Value(double total,String totals,String groups) {}

    private void requireOwner(User user,TrainingSession session) {
        if(!user.getId().equals(session.getUser().getId()))throw new BusinessException(403,"只能登记或查看本人的训练成绩");
    }
    @Transactional
    public AttemptView register(HttpServletRequest request,Long id,ScoreAttemptRequest req) {
        User user=auth.currentUser(request);
        venue.lock();
        TrainingSession session=sessions.findLockedById(id).orElseThrow(()->new BusinessException(404,"训练不存在"));
        requireOwner(user,session);
        Value value=validate(req);
        var previous=attempts.findBySessionIdAndRequestKey(id,req.getRequestKey());
        if(previous.isPresent()) {
            ScoreAttempt saved=previous.get();
            if(Double.compare(saved.getTotalScore(),value.total())!=0 || (!saved.getGroupTotals().equals(value.totals()) || !saved.getMode().name().equals(req.getMode())))
                throw new BusinessException(409,"该次登记已保存，请刷新成绩列表");
            return view(saved); // Network retry is safe even if finishing committed in the meantime.
        }
        boolean completed=session.getEndedAt()!=null && session.getBooking().getStatus()==Booking.BookingStatus.COMPLETED;
        boolean active=session.getEndedAt()==null && session.getBooking().getStatus()==Booking.BookingStatus.IN_USE;
        if(!completed&&!active)throw new BusinessException(409,"当前训练状态不可登记成绩");
        ScoreAttempt saved=attempts.save(ScoreAttempt.builder().session(session).mode(Score.ScoreMode.valueOf(req.getMode())).requestKey(req.getRequestKey())
                .totalScore(value.total()).groupTotals(value.totals()).shotScores("[]").groupScores(value.groups())
                .recordedAt(LocalDateTime.now(clock)).build());
        if(completed)finalizeBest(session);
        return view(saved);
    }
    @Transactional(readOnly=true)
    public Results detail(HttpServletRequest request,Long id) {
        User user=auth.currentUser(request);
        TrainingSession session=sessions.findById(id).orElseThrow(()->new BusinessException(404,"训练不存在"));requireOwner(user,session);
        return detail(session);
    }
    Results detail(TrainingSession session) {
        Long id=session.getId();
        var list=attempts.findBySessionIdOrderByRecordedAtAscIdAsc(id);
        var published=scores.findBySessionIdOrderByModeAsc(id);
        List<ModeResult> byMode=new ArrayList<>();
        for(Score.ScoreMode mode:Score.ScoreMode.values()) {
            Double finalTotal=published.stream().filter(score->score.getMode()==mode).map(Score::getTotalScore).findFirst().orElse(null);
            Double best=list.stream().filter(a->a.getMode()==mode).map(ScoreAttempt::getTotalScore).max(Double::compareTo).orElse(null);
            if(finalTotal!=null)best=best==null?finalTotal:Math.max(best,finalTotal);
            if(best!=null)byMode.add(new ModeResult(mode.name(),best,finalTotal));
        }
        String reason=resumePolicy.reason(session);
        // Compatibility scalars only describe a single-mode record, never compare 24 and 60 shots.
        ModeResult single=byMode.size()==1?byMode.getFirst():null;
        return new Results(TrainingVO.from(session),list.stream().map(this::view).toList(),byMode,
                single==null?null:single.bestTotal(),single==null?null:single.finalTotal(),reason==null,reason);
    }
    /** Caller holds venue/session locks; publish one maximum per mode in the same transaction. */
    public List<Score> finalizeBest(TrainingSession session) {
        return finalizeBest(session,null,null,false);
    }
    // A coach correction may lower a published result; all other modes retain their results.
    List<Score> finalizeBest(TrainingSession session,Score.ScoreMode correctedMode,User coach,boolean preserveLegacy) {
        var list=attempts.findBySessionIdOrderByRecordedAtAscIdAsc(session.getId());
        for(Score.ScoreMode mode:Score.ScoreMode.values()) {
            if(correctedMode!=null && mode!=correctedMode)continue;
            var candidates=list.stream().filter(a->a.getMode()==mode).toList();
            if(candidates.isEmpty())continue;
            Score existing=scores.findBySessionIdAndMode(session.getId(),mode).orElse(null);
            ScoreAttempt best=candidates.stream().min(Comparator.comparing(ScoreAttempt::getTotalScore).reversed()
                    .thenComparing(ScoreAttempt::getRecordedAt).thenComparing(ScoreAttempt::getId)).orElseThrow();
            if((mode!=correctedMode || preserveLegacy) && existing!=null && existing.getTotalScore()>=best.getTotalScore())continue;
            if(mode==correctedMode && existing==null && session.getEndedAt()==null)continue;
            List<Integer> spec=spec(mode);
            Score result=existing==null?Score.builder().session(session).recordedAt(session.getEndedAt()).build():existing;
            result.setMode(mode);
            result.setTotalScore(best.getTotalScore());result.setShotCount(spec.stream().mapToInt(Integer::intValue).sum());
            result.setShotScores(best.getShotScores());result.setGroupScores(best.getGroupScores());result.setGroupSpec(encode(spec));
            result.setXCount(null);result.setRecordedBy(mode==correctedMode?coach:session.getUser());
            scores.save(result);
        }
        return scores.findBySessionIdOrderByModeAsc(session.getId());
    }
    @Transactional(readOnly=true)
    public History history(HttpServletRequest request) { return history(auth.currentUser(request).getId()); }
    public History history(Long userId) {
        var all=scores.findPublishedByUserId(userId).stream().sorted(Comparator.comparing(Score::getRecordedAt).thenComparing(Score::getId)).toList();
        Map<String,HistoryRow> best=new LinkedHashMap<>();List<HistoryRow> rows=new ArrayList<>();
        for(Score score:all) {
            String weapon=score.getSession().getDevice().getType().name(),mode=score.getMode().name(),key=weapon+":"+mode;
            boolean record=!best.containsKey(key)||score.getTotalScore()>best.get(key).totalScore();
            HistoryRow row=new HistoryRow(score.getSession().getId(),weapon,mode,score.getTotalScore(),score.getRecordedAt(),record);
            rows.add(row);if(record)best.put(key,row);
        }
        Collections.reverse(rows);
        Set<Long> scoredIds=new HashSet<>();for(HistoryRow row:rows)scoredIds.add(row.sessionId());
        var unscored=sessions.findByUserId(userId).stream().filter(s->(s.getBooking().getStatus()==Booking.BookingStatus.IN_USE||s.getBooking().getStatus()==Booking.BookingStatus.COMPLETED)&&!scoredIds.contains(s.getId()))
                .sorted(Comparator.comparing(TrainingSession::getStartedAt).reversed()).map(TrainingVO::from).toList();
        return new History(List.copyOf(best.values()),rows,unscored);
    }
    public boolean isPersonalBest(Score score) {
        if(score==null)return false;
        return history(score.getSession().getUser().getId()).personalBests().stream().anyMatch(row->row.sessionId().equals(score.getSession().getId())&&row.mode().equals(score.getMode().name()));
    }
    Value validate(ScoreAttemptRequest req) {
        List<Integer> spec="final_".equals(req.getMode())?List.of(10,10,4):List.of(10,10,10,10,10,10);
        List<Double> totals=req.getGroupTotals();
        if(totals==null || totals.size()!=spec.size())throw new BusinessException(400,"决赛须登记 3 组（10+10+4 发），资格赛须登记 6 组（每组 10 发）");
        List<Double> means=new ArrayList<>();
        for(int i=0;i<spec.size();i++) {
            validNumber(totals.get(i),spec.get(i)*10,"第 "+(i+1)+" 组成绩");
            means.add(Math.round(totals.get(i)/spec.get(i)*10)/10.0);
        }
        double total=Math.round(totals.stream().mapToDouble(Double::doubleValue).sum()*10)/10.0;
        return new Value(total,encode(totals),encode(means));
    }
    void validNumber(Double value,int max,String label) {
        if(value==null || !Double.isFinite(value) || value<0 || value>max || BigDecimal.valueOf(value).stripTrailingZeros().scale()>1)
            throw new BusinessException(400,label+"须为 0–"+max+" 环，最多一位小数");
    }
    private List<Integer> spec(Score.ScoreMode mode) { return mode==Score.ScoreMode.final_?List.of(10,10,4):List.of(10,10,10,10,10,10); }
    private AttemptView view(ScoreAttempt a) {return new AttemptView(a.getId(),a.getMode().name(),a.getTotalScore(),a.getGroupTotals(),a.getRecordedAt());}
    private String encode(Object v) {try{return json.writeValueAsString(v);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
}
