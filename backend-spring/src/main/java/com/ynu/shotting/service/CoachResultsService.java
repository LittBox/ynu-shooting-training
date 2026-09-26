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
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class CoachResultsService {
    private final AuthContext auth;
    private final VenueAccess venue;
    private final TrainingSessionRepository sessions;
    private final ScoreAttemptRepository attempts;
    private final ScoreRepository scores;
    private final AuditLogRepository audits;
    private final TrainingResultsService results;
    private final ObjectMapper json;
    private final Clock clock;

    public record Member(Long id,String name,String studentNo) {}
    public record Row(Member member,TrainingVO session) {}
    public record Records(List<Row> records,boolean hasMore) {}
    public record Editable(Long id,String mode,double totalScore,String groupTotals,String state,boolean legacy) {}
    public record Detail(Member member,TrainingResultsService.Results results,List<Editable> editable,String weapon) {}
    public record DayCard(LocalDate date,long members,long sessions) {}
    public record Days(List<DayCard> days,boolean hasMore) {}
    public record DaySession(TrainingVO session,int rounds,Double bestTotal) {}
    public record RankedMember(Member member,Integer rank,Double bestTotal,int rounds,List<DaySession> sessions) {}
    public record DayGroup(String weapon,String mode,List<RankedMember> members) {}
    public record DayResults(LocalDate date,long memberCount,int sessionCount,List<DayGroup> groups) {}

    @Transactional(readOnly=true)
    public Days days(HttpServletRequest request,int page) {
        coach(request);
        if(page<0 || page>100000)throw new BusinessException(400,"页码不正确");
        var slice=sessions.findCoachDays(PageRequest.of(page,30));
        return new Days(slice.stream().map(d->new DayCard(d.getDate(),d.getMembers(),d.getSessions())).toList(),slice.hasNext());
    }
    @Transactional(readOnly=true)
    public DayResults day(HttpServletRequest request,LocalDate date) {
        coach(request);
        var daySessions=sessions.findCoachDay(date.atStartOfDay(),date.plusDays(1).atStartOfDay());
        var ids=daySessions.stream().map(TrainingSession::getId).toList();
        if(ids.isEmpty())return new DayResults(date,0,0,List.of());
        var byAttempt=attempts.findBySessionIdIn(ids).stream().collect(java.util.stream.Collectors.groupingBy(a->a.getSession().getId()));
        var byScore=scores.findBySessionIdIn(ids).stream().collect(java.util.stream.Collectors.groupingBy(s->s.getSession().getId()));
        List<DayGroup> groups=new ArrayList<>();
        // Rank like-for-like only: a 24-shot final never competes with a 60-shot qualification.
        for(Device.DeviceType weapon:Device.DeviceType.values())for(Score.ScoreMode mode:Score.ScoreMode.values()) {
            Map<Long,List<DaySession>> perMember=new LinkedHashMap<>();
            Map<Long,Member> members=new HashMap<>();
            for(var session:daySessions) {
                if(session.getDevice().getType()!=weapon)continue;
                var allRounds=byAttempt.getOrDefault(session.getId(),List.of());
                var published=byScore.getOrDefault(session.getId(),List.of());
                var rounds=allRounds.stream().filter(a->a.getMode()==mode).toList();
                Double best=rounds.stream().map(ScoreAttempt::getTotalScore).max(Double::compareTo).orElse(null);
                Double legacy=published.stream().filter(s->s.getMode()==mode).map(Score::getTotalScore).max(Double::compareTo).orElse(null);
                if(legacy!=null)best=best==null?legacy:Math.max(best,legacy);
                boolean unscored=allRounds.isEmpty() && published.isEmpty() && session.getMode().name().equals(mode.name());
                if(best==null && !unscored)continue;
                Long memberId=session.getUser().getId();
                members.putIfAbsent(memberId,member(session.getUser()));
                perMember.computeIfAbsent(memberId,key->new ArrayList<>()).add(new DaySession(TrainingVO.from(session),rounds.size(),best));
            }
            List<RankedMember> ordered=new ArrayList<>();
            for(var entry:perMember.entrySet()) {
                var entries=entry.getValue();
                Double best=entries.stream().map(DaySession::bestTotal).filter(Objects::nonNull).max(Double::compareTo).orElse(null);
                ordered.add(new RankedMember(members.get(entry.getKey()),null,best,entries.stream().mapToInt(DaySession::rounds).sum(),List.copyOf(entries)));
            }
            ordered.sort(Comparator.comparing(RankedMember::bestTotal,Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(row->row.member().id()));
            List<RankedMember> ranked=new ArrayList<>();
            Double previous=null;Integer rank=null;
            for(int i=0;i<ordered.size();i++) {
                var row=ordered.get(i);
                if(row.bestTotal()!=null && !row.bestTotal().equals(previous))rank=i+1;
                ranked.add(new RankedMember(row.member(),row.bestTotal()==null?null:rank,row.bestTotal(),row.rounds(),row.sessions()));
                previous=row.bestTotal();
            }
            if(!ranked.isEmpty())groups.add(new DayGroup(weapon.name(),mode.name(),ranked));
        }
        return new DayResults(date,daySessions.stream().map(s->s.getUser().getId()).distinct().count(),daySessions.size(),groups);
    }

    private User coach(HttpServletRequest request) {
        User user=auth.currentUser(request);
        if(user.getRole()!=User.Role.admin && user.getRole()!=User.Role.superadmin)
            throw new BusinessException(403,"仅教练员可查看和更正学员成绩");
        return user;
    }
    private Member member(User user) {
        var profile=user.getProfile();
        return new Member(user.getId(),profile==null?"未登记姓名":profile.getRealName(),profile==null?"":profile.getStudentNo());
    }
    @Transactional(readOnly=true)
    public Records list(HttpServletRequest request,String search,int page) {
        coach(request);
        if(page<0 || page>100000 || search.length()>100)throw new BusinessException(400,"查询条件不正确");
        var slice=sessions.findForCoach(search.trim(),PageRequest.of(page,30,Sort.by(Sort.Order.desc("startedAt"),Sort.Order.desc("id"))));
        return new Records(slice.stream().map(s->new Row(member(s.getUser()),TrainingVO.from(s))).toList(),slice.hasNext());
    }
    @Transactional(readOnly=true)
    public Detail detail(HttpServletRequest request,Long id) {
        coach(request);
        return detail(sessions.findById(id).orElseThrow(()->new BusinessException(404,"训练不存在")));
    }
    private Detail detail(TrainingSession session) {
        var rounds=attempts.findBySessionIdOrderByRecordedAtAscIdAsc(session.getId());
        List<Editable> editable=new ArrayList<>();
        for(var a:rounds)editable.add(new Editable(a.getId(),a.getMode().name(),a.getTotalScore(),a.getGroupTotals(),state(a),false));
        for(var s:scores.findBySessionIdOrderByModeAsc(session.getId()))
            if(!backedByRound(s,rounds))
                editable.add(new Editable(s.getId(),s.getMode().name(),s.getTotalScore(),null,state(s),true));
        return new Detail(member(session.getUser()),results.detail(session),editable,session.getDevice().getType().name());
    }
    @Transactional
    public Detail correct(HttpServletRequest request,Long id,Long recordId,boolean legacy,ScoreCorrectionRequest req) {
        User editor=coach(request);
        venue.lock();
        TrainingSession session=sessions.findLockedById(id).orElseThrow(()->new BusinessException(404,"训练不存在"));
        if(session.getBooking().getStatus()!=Booking.BookingStatus.COMPLETED && session.getBooking().getStatus()!=Booking.BookingStatus.IN_USE)
            throw new BusinessException(409,"当前训练状态不可更正成绩");
        Object before,after;
        if(legacy) {
            Score score=scores.findById(recordId).orElseThrow(()->new BusinessException(404,"成绩不存在"));
            if(!score.getSession().getId().equals(id))throw new BusinessException(404,"成绩不属于此训练");
            if(backedByRound(score,attempts.findBySessionIdOrderByRecordedAtAscIdAsc(id)))
                throw new BusinessException(409,"此模式已有逐轮成绩，请更正对应轮次");
            check(req.expectedState(),state(score));
            results.validNumber(req.totalScore(),score.getMode()==Score.ScoreMode.final_?240:600,"总成绩");
            before=snapshot(score);
            score.setTotalScore(req.totalScore());
            // Correcting an old total must not leave contradictory per-shot data behind.
            score.setShotScores("[]");score.setGroupScores("[]");score.setXCount(null);score.setRecordedBy(editor);
            scores.saveAndFlush(score);after=snapshot(score);
            results.finalizeBest(session,score.getMode(),editor,true);
        } else {
            ScoreAttempt round=attempts.findById(recordId).orElseThrow(()->new BusinessException(404,"轮次不存在"));
            if(!round.getSession().getId().equals(id))throw new BusinessException(404,"轮次不属于此训练");
            check(req.expectedState(),state(round));
            ScoreAttemptRequest values=new ScoreAttemptRequest();values.setMode(round.getMode().name());values.setGroupTotals(req.groupTotals());
            var value=results.validate(values);before=snapshot(round);
            var published=scores.findBySessionIdAndMode(id,round.getMode()).orElse(null);
            boolean preserveLegacy=published!=null && !backedByRound(published,attempts.findBySessionIdOrderByRecordedAtAscIdAsc(id));
            round.setTotalScore(value.total());round.setGroupTotals(value.totals());round.setGroupScores(value.groups());round.setShotScores("[]");
            attempts.saveAndFlush(round);after=snapshot(round);
            // Active sessions without a published result stay unpublished until finish.
            results.finalizeBest(session,round.getMode(),editor,preserveLegacy);
        }
        audits.save(AuditLog.builder().admin(editor).action("CORRECT_TRAINING_SCORE").targetUserId(session.getUser().getId())
                .detail(encode(Map.of("sessionId",id,"recordId",recordId,"legacy",legacy,"reason",req.reason().trim(),"before",before,"after",after)))
                .createdAt(LocalDateTime.now(clock)).build());
        return detail(session);
    }
    // A standalone legacy final score may coexist with later, lower supplementary rounds.
    private boolean backedByRound(Score score,List<ScoreAttempt> rounds) {
        return rounds.stream().anyMatch(a->a.getMode()==score.getMode() && Double.compare(a.getTotalScore(),score.getTotalScore())==0);
    }
    private void check(String expected,String actual) {
        if(!actual.equals(expected))throw new BusinessException(409,"成绩已被修改，请重新加载后再更正");
    }
    private Map<String,Object> snapshot(ScoreAttempt a) {
        return Map.of("id",a.getId(),"mode",a.getMode().name(),"totalScore",a.getTotalScore(),"groupTotals",a.getGroupTotals(),"recordedAt",a.getRecordedAt().toString());
    }
    private Map<String,Object> snapshot(Score s) {
        Map<String,Object> value=new LinkedHashMap<>();
        value.put("id",s.getId());value.put("mode",s.getMode().name());value.put("totalScore",s.getTotalScore());
        value.put("shotScores",s.getShotScores());value.put("groupScores",s.getGroupScores());value.put("xCount",s.getXCount());
        value.put("recordedBy",s.getRecordedBy()==null?null:s.getRecordedBy().getId());value.put("recordedAt",s.getRecordedAt().toString());
        return value;
    }
    private String state(ScoreAttempt a) {return digest(encode(new TreeMap<>(snapshot(a))));}
    private String state(Score s) {return digest(encode(new TreeMap<>(snapshot(s))));}
    private String digest(String value) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private String encode(Object value) {
        try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}
    }
}
