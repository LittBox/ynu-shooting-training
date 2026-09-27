package com.ynu.shoting.service.impl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.dto.*;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.exception.BusinessException;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.AuthContext;
import com.ynu.shoting.service.*;
import com.ynu.shoting.websocket.SlotPushService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class TrainingServiceImpl implements TrainingService {
    private final TrainingResultsService results;
    private final TrainingResumePolicy resumePolicy;
    private final VenueAccess venue;
    private final VenueDutyService duty;
    private final BookingRepository bookings;
    private final TrainingSessionRepository sessions;
    private final ScoreRepository scores;
    private final BookingAccess access;
    private final AuthContext auth;
    private final ObjectMapper json;
    private final Clock clock;
    private final SlotPushService push;

    @Override @Transactional
    public TrainingVO start(HttpServletRequest request, Long bookingId, String mode) {
        User user=auth.currentUser(request);
        venue.lock();
        Booking b=access.lock(bookingId);
        access.requireOwner(user,b);
        TrainingSession.SessionMode event=TrainingSession.SessionMode.valueOf(mode);
        if (b.getStatus()!=Booking.BookingStatus.CHECKED_IN) throw new BusinessException(409,"必须先签到才能开始训练");
        if (b.getDevice().getStatus()!=Device.DeviceStatus.IDLE && b.getDevice().getStatus()!=Device.DeviceStatus.BOOKED)
            throw new BusinessException(409,"设备当前不可开始训练，请联系管理员");
        LocalDateTime now=LocalDateTime.now(clock);
        LocalDateTime slotEnd=LocalDateTime.of(LocalDate.parse(b.getSlotDate()),com.ynu.shoting.config.Slot.fromId(b.getSlotId()).getEnd());
        if (!now.isBefore(slotEnd)) throw new BusinessException(409,"预约时段已结束，无法开始训练");
        duty.requirePresent(b.getSlotDate(), b.getSlotId());
        b.setStatus(Booking.BookingStatus.IN_USE); b.setStartedAt(now);
        b.getDevice().setStatus(Device.DeviceStatus.IN_USE);
        TrainingSession session=sessions.save(TrainingSession.builder().booking(b).user(b.getUser()).device(b.getDevice())
                .mode(event).startedAt(now).createdAt(now).build());
        b.setTrainingSession(session);
        push.pushDate(b.getSlotDate());
        return TrainingVO.from(session);
    }
    @Override @Transactional
    public TrainingVO resume(HttpServletRequest request, Long sessionId) {
        User user=auth.currentUser(request);
        venue.lock();
        Long bookingId=sessions.findBookingId(sessionId).orElseThrow(()->new BusinessException(404,"训练记录不存在"));
        Booking b=access.lock(bookingId);
        TrainingSession session=sessions.findLockedById(sessionId).orElseThrow();
        if(!session.getUser().getId().equals(user.getId()))throw new BusinessException(403,"仅可继续本人的训练");
        if(b.getStatus()==Booking.BookingStatus.IN_USE && session.getEndedAt()==null)return TrainingVO.from(session);
        String reason=resumePolicy.reason(session);
        if(reason!=null)throw new BusinessException(409,reason);
        // Release stale keys written by older deployments before restoring this occupancy.
        for(Booking previous:bookings.findBySlotDateAndSlotId(b.getSlotDate(),b.getSlotId())) {
            if(previous.getDevice().getId().equals(b.getDevice().getId()) && previous.getStatus()==Booking.BookingStatus.COMPLETED)
                previous.setOccupiedDeviceSlot(null);
        }
        bookings.flush();
        session.setResumeStartedAt(LocalDateTime.now(clock));
        session.setEndedAt(null);
        b.setStatus(Booking.BookingStatus.IN_USE);b.setEndedAt(null);
        b.getDevice().setStatus(Device.DeviceStatus.IN_USE);
        push.pushDate(b.getSlotDate());
        return TrainingVO.from(session);
    }
    @Override @Transactional
    public TrainingVO finish(HttpServletRequest request, Long sessionId) {
        User user=auth.currentUser(request);
        venue.lock();
        Long bookingId=sessions.findBookingId(sessionId).orElseThrow(() -> new BusinessException(404,"训练记录不存在"));
        Booking b=access.lock(bookingId); access.requireOwner(user,b);
        TrainingSession session=sessions.findLockedById(sessionId).orElseThrow();
        if (b.getStatus()!=Booking.BookingStatus.IN_USE || session.getEndedAt()!=null)
            throw new BusinessException(409,"仅训练中的记录可以结束");
        LocalDateTime now=LocalDateTime.now(clock);
        session.setEndedAt(now);
        long duration=session.getResumeStartedAt()==null?Duration.between(session.getStartedAt(),now).toMinutes()
                : Objects.requireNonNullElse(session.getActualDurationMin(),0)+Math.max(0,Duration.between(session.getResumeStartedAt(),now).toMinutes());
        session.setActualDurationMin((int)Math.max(duration,1));
        session.setResumeStartedAt(null);
        b.setStatus(Booking.BookingStatus.COMPLETED); b.setEndedAt(now);
        // A maintenance override must not be silently removed by finishing training.
        if (b.getDevice().getStatus()==Device.DeviceStatus.IN_USE) b.getDevice().setStatus(Device.DeviceStatus.IDLE);
        List<Score> finalScores=results.finalizeBest(session);
        push.pushDate(b.getSlotDate());
        return TrainingVO.finished(session, finalScores.stream().map(score->new TrainingVO.ModeScore(score.getMode().name(),score.getTotalScore(),results.isPersonalBest(score))).toList());
    }
    @Override @Transactional
    public ScoreVO submitScore(HttpServletRequest request, ScoreRequest req) {
        User admin=auth.currentUser(request);
        if (admin.getRole()==User.Role.student) throw new BusinessException(403,"仅管理员可录入成绩");
        venue.lock();
        TrainingSession session=sessions.findLockedById(req.getSessionId()).orElseThrow(() -> new BusinessException(404,"训练记录不存在"));
        if(session.isHistorical())throw new BusinessException(409,"历史补录成绩请通过教员成绩管理更正");
        if (session.getEndedAt()==null || session.getBooking().getStatus()!=Booking.BookingStatus.COMPLETED)
            throw new BusinessException(409,"训练完成后才能录入成绩");
        String mode=req.getMode()==null ? session.getMode().name() : req.getMode();
        if ("final".equals(mode)) mode="final_";
        if (!List.of("final_","qualifying").contains(mode)) throw new BusinessException(400,"请选择决赛或资格赛模式");
        if (scores.findBySessionIdAndMode(session.getId(),Score.ScoreMode.valueOf(mode)).isPresent()) throw new BusinessException(409,"该训练此模式的最终成绩已录入");
        List<Integer> spec="final_".equals(mode) ? List.of(10,10,4) : List.of(10,10,10,10,10,10);
        if (!spec.equals(req.getGroupSpec()) || req.getShotScores().size()!=spec.stream().mapToInt(Integer::intValue).sum())
            throw new BusinessException(400,"决赛须24发（10+10+4），资格赛须60发（6×10）");
        List<Double> shots=req.getShotScores();
        if (shots.stream().anyMatch(v -> v==null || !Double.isFinite(v) || v<0 || v>10))
            throw new BusinessException(400,"每发成绩须在0至10环之间");
        int x=req.getXCount()==null ? 0 : req.getXCount();
        if (x<0 || x>shots.stream().filter(v -> v==10).count()) throw new BusinessException(400,"X环数量不能超过10环发数");
        List<Double> means=new ArrayList<>(); int offset=0;
        for (int size:spec) {
            means.add(Math.round(shots.subList(offset,offset+size).stream().mapToDouble(Double::doubleValue).average().orElseThrow()*10)/10.0);
            offset+=size;
        }
        Score score=scores.save(Score.builder().session(session).mode(Score.ScoreMode.valueOf(mode))
            .shotScores(toJson(shots)).groupScores(toJson(means)).groupSpec(toJson(spec)).shotCount(shots.size())
            .totalScore(Math.round(shots.stream().mapToDouble(Double::doubleValue).sum()*10)/10.0)
            .xCount(x).recordedBy(admin).recordedAt(LocalDateTime.now(clock)).build());
        return ScoreVO.from(score);
    }
    @Override @Transactional(readOnly=true)
    public List<TrainingVO> mySessions(HttpServletRequest request) {
        return sessions.findByUserId(auth.currentUser(request).getId()).stream().map(TrainingVO::from).toList();
    }
    private String toJson(Object value) {
        try { return json.writeValueAsString(value); } catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
}
