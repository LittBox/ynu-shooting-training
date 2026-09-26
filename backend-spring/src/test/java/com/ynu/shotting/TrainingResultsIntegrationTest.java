package com.ynu.shoting;

import com.fasterxml.jackson.databind.*;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.name=booking-flow-test", "spring.datasource.url=${test.database.url:jdbc:h2:mem:training-results-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}"})
@AutoConfigureMockMvc
@Import(BusinessIntegrationTest.TimeFixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class TrainingResultsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BusinessIntegrationTest.MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired DeviceRepository devices;
    @Autowired AdminScheduleRepository schedules;
    @Autowired BookingRepository bookings;
    @Autowired TrainingSessionRepository sessions;
    @Autowired AuditLogRepository audits;
    @Autowired JwtTokenProvider tokens;
    @Autowired PlatformTransactionManager transactions;
    User coach, relief, student, other;
    Device pistol, rifle;
    long shift;
    String tc, tr, ts, to;
    final String date="2026-09-25";

    @BeforeEach void setup() {
        clock.set("2026-09-25T08:00:00+08:00");
        new TransactionTemplate(transactions).executeWithoutResult(t -> {
            for(String table:List.of("score_attempts","scores","training_sessions","cancellation_log","no_show_records","bookings","admin_schedules","user_availability","audit_log","profiles","users","devices"))
                jdbc.update("delete from "+table);
            coach=user("coach",User.Role.admin); relief=user("relief",User.Role.admin);
            student=user("student",User.Role.student); other=user("other",User.Role.student);
            pistol=devices.save(Device.builder().name("手枪").type(Device.DeviceType.pistol).build());
            rifle=devices.save(Device.builder().name("步枪").type(Device.DeviceType.rifle).build());
            shift=schedules.save(AdminSchedule.builder().admin(coach).slotDate(date).slotId("S1").build()).getId();
        });
        tc=token(coach); tr=token(relief); ts=token(student); to=token(other);
    }
    User user(String name,User.Role role) {
        User user = users.save(User.builder().openid("duty-"+name).nickname(name).role(role).profileStatus(User.ProfileStatus.completed).build());
        user.setProfile(profiles.save(Profile.builder().user(user).realName(name).studentNo("duty-"+name).phone("13800000000").gender("M").build()));
        return user;
    }
    String token(User u){return "Bearer "+tokens.generateToken(u);}
    JsonNode data(ResultActions action)throws Exception{return json.readTree(action.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    ResultActions add(User who,String day,String slot)throws Exception {
        return mvc.perform(post("/api/admin/schedules").header("Authorization",tc)
                .param("adminId",who.getId().toString()).param("slotDate",day).param("slotId",slot));
    }
    ResultActions arrive(String token,long id)throws Exception{return mvc.perform(post("/api/admin/schedules/"+id+"/arrive").header("Authorization",token));}
    ResultActions leave(String token)throws Exception{return mvc.perform(post("/api/admin/duty/depart").header("Authorization",token));}
    ResultActions bookResponse(String token,Device device,String slot)throws Exception {
        return mvc.perform(post("/api/bookings").header("Authorization",token).contentType("application/json")
                .content(json.writeValueAsString(Map.of("deviceId",device.getId(),"slotDate",date,"slotId",slot,"availabilityConfirmed",true))));
    }
    long checkedIn(String token,Device device,String slot)throws Exception {
        long id=data(bookResponse(token,device,slot).andExpect(status().isOk())).path("id").asLong();
        mvc.perform(post("/api/bookings/"+id+"/checkin").header("Authorization",token)).andExpect(status().isOk());
        return id;
    }
    ResultActions start(String token,long id)throws Exception{return mvc.perform(post("/api/training/start/"+id).header("Authorization",token));}
    void finish(String token,long session)throws Exception{mvc.perform(post("/api/training/finish/"+session).header("Authorization",token)).andExpect(status().isOk());}

    long session(String token,Device device,String slot)throws Exception {
        long booking=checkedIn(token,device,slot);arrive(tc,shift).andExpect(status().isOk());
        return data(start(token,booking).andExpect(status().isOk())).path("id").asLong();
    }
    ResultActions attempt(String token,long session,String key,String mode,List<Double> values)throws Exception {
        var req=post("/api/training/"+session+"/attempts").contentType("application/json")
                .content(json.writeValueAsString(Map.of("requestKey",key,"mode",mode,"groupTotals",values)));
        if(token!=null)req.header("Authorization",token);return mvc.perform(req);
    }
    ResultActions finishResponse(String token,long id)throws Exception {return mvc.perform(post("/api/training/finish/"+id).header("Authorization",token));}
    JsonNode history(String token)throws Exception {return data(mvc.perform(get("/api/training/records/me").header("Authorization",token)).andExpect(status().isOk()));}
    @Test void finalRoundsAreSummedAndOnlyHighestIsPublishedAtFinish()throws Exception {
        long id=session(ts,pistol,"S1");
        attempt(ts,id,"a","final_",List.of(90.0,91.0,35.0)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalScore").value(216.0));
        attempt(ts,id,"b","final_",List.of(80.0,90.0,30.0)).andExpect(status().isOk());
        attempt(ts,id,"c","final_",List.of(94.0,95.0,38.0)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(0,history(ts).path("sessions").size());
        mvc.perform(get("/api/training/"+id+"/results").header("Authorization",ts)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bestTotal").value(227.0)).andExpect(jsonPath("$.data.finalTotal").isEmpty());
        finishResponse(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalScore").value(227.0)).andExpect(jsonPath("$.data.personalBest").value(true));
        assertEquals(1,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(3,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
        assertEquals(227.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        mvc.perform(get("/api/leaderboard").header("Authorization",ts).param("weapon","PISTOL").param("event","FINAL").param("metric","BEST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].score").value(227.0));
        finishResponse(ts,id).andExpect(status().isConflict());
    }
    @Test void qualifyingAcceptsSixTenShotGroupsWithModeOnTheRound()throws Exception {
        long id=session(ts,pistol,"S1");
        attempt(ts,id,"q","qualifying",List.of(90.0,91.0,92.0,93.0,94.0,95.0)).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalScore").value(555.0));
        assertEquals("qualifying",jdbc.queryForObject("select mode from score_attempts",String.class));
        finishResponse(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalScore").value(555.0));
        assertEquals("qualifying",history(ts).at("/sessions/0/mode").asText());
        assertEquals("[]",jdbc.queryForObject("select shot_scores from scores",String.class));
        assertEquals("[9.0,9.1,9.2,9.3,9.4,9.5]",jdbc.queryForObject("select group_scores from scores",String.class));
    }
    @Test void ownerOnlyAndIdempotentRetriesPreserveAllSavedRounds()throws Exception {
        long id=session(ts,pistol,"S1");var groups=List.of(90.0,90.0,36.0);
        attempt(null,id,"a","final_",groups).andExpect(status().isUnauthorized());
        attempt(to,id,"a","final_",groups).andExpect(status().isForbidden());
        attempt(tc,id,"a","final_",groups).andExpect(status().isForbidden());
        mvc.perform(get("/api/training/"+id+"/results").header("Authorization",to)).andExpect(status().isForbidden());
        long first=data(attempt(ts,id,"a","final_",groups).andExpect(status().isOk())).path("id").asLong();
        assertEquals(first,data(attempt(ts,id,"a","final_",groups).andExpect(status().isOk())).path("id").asLong());
        attempt(ts,id,"a","final_",List.of(91.0,89.0,36.0)).andExpect(status().isConflict());
        finishResponse(ts,id).andExpect(status().isOk());
        attempt(ts,id,"a","final_",groups).andExpect(status().isOk());
        attempt(ts,id,"new","final_",groups).andExpect(status().isOk());
        assertEquals(2,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
    }
    @Test void validatesEveryGroupIncludingFinalFourShotsAndDoesNotMutateOnFailure()throws Exception {
        long id=session(ts,pistol,"S1");
        for(var groups:List.of(List.of(90.0,90.0),List.of(101.0,90.0,36.0),List.of(90.0,90.0,40.1),List.of(-1.0,90.0,36.0),List.of(90.01,90.0,36.0)))
            attempt(ts,id,"bad","final_",groups).andExpect(status().isBadRequest());
        attempt(ts,id,"bad","qualifying",List.of(90.0,90.0,36.0)).andExpect(status().isBadRequest());
        attempt(ts,id,"bad","invalid",List.of(90.0,90.0,36.0)).andExpect(status().isBadRequest());
        assertEquals(0,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
        assertEquals(TrainingSession.SessionMode.final_,sessions.findById(id).orElseThrow().getMode());
        attempt(ts,id,"zero","final_",List.of(0.0,0.0,0.0)).andExpect(status().isOk());
    }
    @Test void newPersonalBestReplacesOldButLowerAndTiedResultsDoNot()throws Exception {
        for(int index=0;index<4;index++) {
            String slot="S"+(index+1);if(index>0) {
                clock.set("2026-09-25T"+List.of("08:00","10:00","13:30","15:30").get(index)+":00+08:00");
                shift=data(add(coach,date,slot).andExpect(status().isOk())).path("id").asLong();
            }
            long id=session(ts,pistol,slot);
            var values=List.of(List.of(80.0,80.0,30.0),List.of(90.0,90.0,36.0),List.of(85.0,85.0,30.0),List.of(90.0,90.0,36.0)).get(index);
            attempt(ts,id,"round","final_",values).andExpect(status().isOk());
            finishResponse(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.personalBest").value(index<2));
            if(index==2)break; // Daily cap is unchanged; a separate later day exercises the tie.
        }
        long bestId=history(ts).at("/personalBests/0/sessionId").asLong();
        clock.set("2026-09-26T08:00:00+08:00");
        new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(t->{
            var u=users.findById(student.getId()).orElseThrow();var d=devices.findById(pistol.getId()).orElseThrow();
            var b=bookings.save(Booking.builder().user(u).device(d).slotDate("2026-09-26").slotId("S1").status(Booking.BookingStatus.IN_USE).build());
            sessions.save(TrainingSession.builder().booking(b).user(u).device(d).mode(TrainingSession.SessionMode.final_).startedAt(java.time.LocalDateTime.of(2026,9,26,8,0)).build());
        });
        long tied=sessions.findByUserId(student.getId()).stream().mapToLong(TrainingSession::getId).max().orElseThrow();
        attempt(ts,tied,"tie","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        finishResponse(ts,tied).andExpect(status().isOk()).andExpect(jsonPath("$.data.personalBest").value(false));
        assertEquals(bestId,history(ts).at("/personalBests/0/sessionId").asLong());
        assertEquals(4,history(ts).path("sessions").size());assertEquals(216.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        assertEquals(0,history(to).path("sessions").size());
    }
    @Test void finishWithoutRoundsRecordsDurationWithoutInventingScore()throws Exception {
        long id=session(ts,pistol,"S1");finishResponse(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalScore").isEmpty());
        assertEquals(0,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(0,history(ts).path("sessions").size());
    }
    @Test void concurrentRegisterAndFinishAreSerializedWithoutLosingCommittedRound()throws Exception {
        long id=session(ts,pistol,"S1");var result=race(()->code(attempt(ts,id,"race","final_",List.of(90.0,90.0,36.0))),()->code(finishResponse(ts,id)));
        assertEquals(List.of(200,200),result);
        assertEquals(jdbc.queryForObject("select count(*) from score_attempts",Integer.class),jdbc.queryForObject("select count(*) from scores",Integer.class));
    }
    @Test void concurrentDuplicateRoundSubmissionIsOnlyStoredOnce()throws Exception {
        long id=session(ts,pistol,"S1");
        assertEquals(List.of(200,200),race(()->code(attempt(ts,id,"same","final_",List.of(90.0,90.0,36.0))),()->code(attempt(ts,id,"same","final_",List.of(90.0,90.0,36.0)))));
        assertEquals(1,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
    }
    ResultActions resume(String token,long id)throws Exception { return mvc.perform(post("/api/training/resume/"+id).header("Authorization",token)); }
    ResultActions detail(String token,long id)throws Exception { return mvc.perform(get("/api/training/"+id+"/results").header("Authorization",token)); }
    ResultActions walkIn(String token)throws Exception { return mvc.perform(post("/api/bookings/walk-in").header("Authorization",token).contentType("application/json").content(json.writeValueAsString(Map.of("deviceId",pistol.getId(),"slotDate",date,"slotId","S1","mode","final_")))); }
    @Test void lateRoundsUpdateOneFinalScoreAndHistoryEvenAfterSlotEnds()throws Exception {
        long id=session(ts,pistol,"S1");
        assertEquals(id,history(ts).at("/unscoredSessions/0/id").asLong());
        clock.set("2026-09-25T08:10:00+08:00");finish(ts,id);
        assertEquals(id,history(ts).at("/unscoredSessions/0/id").asLong());
        clock.set("2026-09-26T15:00:00+08:00");
        detail(ts,id).andExpect(jsonPath("$.data.canResume").value(false));
        resume(ts,id).andExpect(status().isConflict());
        attempt(ts,id,"late","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        var recorded=jdbc.queryForObject("select recorded_at from scores",java.sql.Timestamp.class);
        assertEquals(java.time.LocalDateTime.of(2026,9,25,8,10),recorded.toLocalDateTime());
        attempt(ts,id,"higher","final_",List.of(95.0,95.0,38.0)).andExpect(status().isOk());
        attempt(ts,id,"lower","final_",List.of(85.0,85.0,30.0)).andExpect(status().isOk());
        assertEquals(1,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(recorded,jdbc.queryForObject("select recorded_at from scores",java.sql.Timestamp.class));
        assertEquals(228.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        assertEquals(0,history(ts).path("unscoredSessions").size());
        assertEquals(Device.DeviceStatus.IDLE,devices.findById(pistol.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/leaderboard").header("Authorization",ts).param("weapon","PISTOL").param("event","FINAL").param("metric","BEST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].score").value(228.0));
    }
    @Test void resumePreservesRecordScoreAndQuotaWhileExcludingPauseFromDuration()throws Exception {
        long id=session(ts,pistol,"S1");
        attempt(ts,id,"first","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        clock.set("2026-09-25T08:10:00+08:00");finish(ts,id);
        detail(ts,id).andExpect(jsonPath("$.data.canResume").value(true));
        resume(to,id).andExpect(status().isForbidden());resume(tc,id).andExpect(status().isForbidden());
        clock.set("2026-09-25T08:20:00+08:00");resume(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id));
        clock.set("2026-09-25T08:22:00+08:00");resume(ts,id).andExpect(status().isOk());
        assertEquals(216.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        assertEquals(1,bookings.count());assertEquals(1,sessions.count());
        assertEquals(1,jdbc.queryForObject("select count(*) from bookings where occupied_device_slot is not null and status='IN_USE'",Integer.class));
        assertEquals(Device.DeviceStatus.IN_USE,devices.findById(pistol.getId()).orElseThrow().getStatus());
        attempt(ts,id,"better","final_",List.of(95.0,95.0,38.0)).andExpect(status().isOk());
        assertEquals(216.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        clock.set("2026-09-25T08:25:00+08:00");finishResponse(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalScore").value(228.0));
        var saved=sessions.findById(id).orElseThrow();
        assertEquals(15,saved.getActualDurationMin());assertEquals(java.time.LocalDateTime.of(2026,9,25,8,0),saved.getStartedAt());
        assertEquals(1,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from bookings where occupied_device_slot is not null",Integer.class));
    }
    @Test void resumeChecksCoachDeviceAndOtherBookingWithoutPreventingScoreEntry()throws Exception {
        long id=session(ts,pistol,"S1");finish(ts,id);
        leave(tc).andExpect(status().isOk());resume(ts,id).andExpect(status().isConflict());
        arrive(tc,shift).andExpect(status().isOk());
        jdbc.update("update devices set status='MAINTENANCE' where id=?",pistol.getId());
        resume(ts,id).andExpect(status().isConflict());
        jdbc.update("update devices set status='IDLE' where id=?",pistol.getId());
        checkedIn(to,pistol,"S1");resume(ts,id).andExpect(status().isConflict());
        attempt(ts,id,"after","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        assertEquals(Booking.BookingStatus.COMPLETED,bookings.findByUserId(student.getId()).getFirst().getStatus());
    }
    @Test void resumeCannotTakeDeviceFromConcurrentWalkIn()throws Exception {
        long id=session(ts,pistol,"S1");finish(ts,id);clock.set("2026-09-25T08:40:00+08:00");
        assertEquals(List.of(200,409),race(()->code(resume(ts,id)),()->code(walkIn(to))));
        assertEquals(1,jdbc.queryForObject("select count(*) from bookings where status='IN_USE'",Integer.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from bookings where occupied_device_slot is not null",Integer.class));
    }
    @Test void legacyFinalScoreIsRetainedWhenLowerRoundsAreAdded()throws Exception {
        long id=session(ts,pistol,"S1");finish(ts,id);
        mvc.perform(post("/api/training/score").header("Authorization",tc).contentType("application/json").content(json.writeValueAsString(Map.of("sessionId",id,"groupSpec",List.of(10,10,4),"shotScores",Collections.nCopies(24,9.5)))))
                .andExpect(status().isOk());
        attempt(ts,id,"lower","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        detail(ts,id).andExpect(jsonPath("$.data.finalTotal").value(228.0));
        attempt(ts,id,"othermode","qualifying",Collections.nCopies(6,90.0)).andExpect(status().isOk());
        attempt(ts,id,"higher","final_",List.of(96.0,96.0,39.0)).andExpect(status().isOk());
        assertEquals(231.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        assertEquals(2,jdbc.queryForObject("select count(*) from scores",Integer.class));
    }
    @Test void mixedRoundsPublishIndependentBestsAndNeverCompareAcrossModes()throws Exception {
        long id=session(ts,pistol,"S1");
        attempt(ts,id,"q1","qualifying",Collections.nCopies(6,90.0)).andExpect(status().isOk());
        attempt(ts,id,"f1","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        attempt(ts,id,"q2","qualifying",Collections.nCopies(6,95.0)).andExpect(status().isOk());
        attempt(ts,id,"f2","final_",List.of(95.0,95.0,38.0)).andExpect(status().isOk());
        detail(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.bestTotal").isEmpty())
                .andExpect(jsonPath("$.data.modeResults[0].mode").value("final_"))
                .andExpect(jsonPath("$.data.modeResults[0].bestTotal").value(228.0))
                .andExpect(jsonPath("$.data.modeResults[1].bestTotal").value(570.0))
                .andExpect(jsonPath("$.data.attempts[0].mode").value("qualifying"));
        finishResponse(ts,id).andExpect(status().isOk()).andExpect(jsonPath("$.data.finalScore").isEmpty())
                .andExpect(jsonPath("$.data.finalScores[0].finalScore").value(228.0))
                .andExpect(jsonPath("$.data.finalScores[1].finalScore").value(570.0));
        assertEquals(2,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(1,sessions.count());assertEquals(1,bookings.count());
        var h=history(ts);assertEquals(2,h.path("personalBests").size());assertEquals(2,h.path("sessions").size());
        for(var item:Map.of("FINAL",228.0,"QUALIFICATION",570.0).entrySet())
            mvc.perform(get("/api/leaderboard").header("Authorization",ts).param("weapon","PISTOL").param("event",item.getKey()).param("metric","BEST"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].score").value(item.getValue()));
        // A retry remains tied to the original round mode, even after other modes were saved.
        attempt(ts,id,"q1","qualifying",Collections.nCopies(6,90.0)).andExpect(status().isOk());
        attempt(ts,id,"q1","final_",List.of(90.0,90.0,36.0)).andExpect(status().isConflict());
        assertEquals(4,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
        attempt(ts,id,"late-f","final_",List.of(96.0,96.0,39.0)).andExpect(status().isOk());
        assertEquals(231.0,jdbc.queryForObject("select total_score from scores where mode='final_'",Double.class));
        assertEquals(570.0,jdbc.queryForObject("select total_score from scores where mode='qualifying'",Double.class));
        resume(ts,id).andExpect(status().isOk());
        attempt(ts,id,"resumed-q","qualifying",Collections.nCopies(6,96.0)).andExpect(status().isOk());
        assertEquals(570.0,jdbc.queryForObject("select total_score from scores where mode='qualifying'",Double.class));
        finish(ts,id);
        assertEquals(576.0,jdbc.queryForObject("select total_score from scores where mode='qualifying'",Double.class));
        assertEquals(2,jdbc.queryForObject("select count(*) from scores",Integer.class));
    }
    @Test void personalBestFlagIsCheckedPerModeNotJustSessionId()throws Exception {
        long first=session(ts,pistol,"S1");
        attempt(ts,first,"q","qualifying",Collections.nCopies(6,95.0)).andExpect(status().isOk());
        attempt(ts,first,"f","final_",List.of(80.0,80.0,30.0)).andExpect(status().isOk());finish(ts,first);
        clock.set("2026-09-25T10:00:00+08:00");shift=data(add(coach,date,"S2").andExpect(status().isOk())).path("id").asLong();
        long second=session(ts,pistol,"S2");
        attempt(ts,second,"q","qualifying",Collections.nCopies(6,90.0)).andExpect(status().isOk());
        attempt(ts,second,"f","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        finishResponse(ts,second).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScores[0].personalBest").value(true))
                .andExpect(jsonPath("$.data.finalScores[1].personalBest").value(false));
    }
    @Test void concurrentMixedModeSubmissionsAndLateModeCreationAreSafe()throws Exception {
        long id=session(ts,pistol,"S1");
        assertEquals(List.of(200,200),race(()->code(attempt(ts,id,"q","qualifying",Collections.nCopies(6,90.0))),()->code(attempt(ts,id,"f","final_",List.of(90.0,90.0,36.0)))));
        finish(ts,id);
        assertEquals(2,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(List.of(200,200),race(()->code(attempt(ts,id,"q-late","qualifying",Collections.nCopies(6,95.0))),()->code(attempt(ts,id,"f-late","final_",List.of(95.0,95.0,38.0)))));
        assertEquals(2,jdbc.queryForObject("select count(*) from scores",Integer.class));
        assertEquals(570.0,jdbc.queryForObject("select total_score from scores where mode='qualifying'",Double.class));
        assertEquals(228.0,jdbc.queryForObject("select total_score from scores where mode='final_'",Double.class));
    }
    int code(ResultActions r){return r.andReturn().getResponse().getStatus();}
    List<Integer> race(Callable<Integer> one,Callable<Integer> two)throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
            List<Future<Integer>> result=new ArrayList<>();
            for(var task:List.of(one,two))result.add(pool.submit(()->{ready.countDown();go.await();return task.call();}));
            assertTrue(ready.await(3,TimeUnit.SECONDS));go.countDown();
            List<Integer> codes=new ArrayList<>();for(var r:result)codes.add(r.get(10,TimeUnit.SECONDS));Collections.sort(codes);return codes;
        }
    }
    JsonNode coachDetail(long id)throws Exception {
        return data(mvc.perform(get("/api/coach/training/"+id).header("Authorization",tc)).andExpect(status().isOk()));
    }
    JsonNode editable(long id,long roundId)throws Exception {
        for(var row:coachDetail(id).path("editable"))if(row.path("id").asLong()==roundId&&!row.path("legacy").asBoolean())return row;
        throw new AssertionError("Missing round");
    }
    ResultActions correct(String token,long id,JsonNode row,List<Double> values)throws Exception {
        return mvc.perform(put("/api/coach/training/"+id+"/attempts/"+row.path("id").asLong()).header("Authorization",token)
            .contentType("application/json").content(json.writeValueAsString(Map.of("expectedState",row.path("state").asText(),"groupTotals",values,"reason","核对平板，修正录入错误"))));
    }
    @Test void coachCorrectionLowersPublishedBestAndPreservesOtherModeAndAudit()throws Exception {
        long id=session(ts,pistol,"S1");
        long high=data(attempt(ts,id,"high","final_",List.of(100.0,100.0,40.0)).andExpect(status().isOk())).path("id").asLong();
        attempt(ts,id,"next","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        attempt(ts,id,"qualifying","qualifying",Collections.nCopies(6,90.0)).andExpect(status().isOk());
        finish(ts,id);
        var row=editable(id,high);
        correct(tc,id,row,List.of(80.0,80.0,30.0)).andExpect(status().isOk());
        assertEquals(216.0,jdbc.queryForObject("select total_score from scores where mode='final_'",Double.class));
        assertEquals(540.0,jdbc.queryForObject("select total_score from scores where mode='qualifying'",Double.class));
        assertEquals(216.0,history(ts).path("personalBests").findValues("totalScore").stream().mapToDouble(JsonNode::asDouble).min().orElseThrow());
        mvc.perform(get("/api/leaderboard").param("weapon","PISTOL").param("event","FINAL").param("metric","BEST"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].score").value(216.0));
        var audit=json.readTree(jdbc.queryForObject("select detail from audit_log where action='CORRECT_TRAINING_SCORE'",String.class));
        assertEquals(240,audit.at("/before/totalScore").asDouble());assertEquals(190,audit.at("/after/totalScore").asDouble());
        assertEquals(coach.getId(),jdbc.queryForObject("select admin_user_id from audit_log where action='CORRECT_TRAINING_SCORE'",Long.class));
        assertEquals(3,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
        correct(tr,id,row,List.of(99.0,99.0,39.0)).andExpect(status().isConflict());
        attempt(ts,id,"high","final_",List.of(100.0,100.0,40.0)).andExpect(status().isConflict());
        attempt(ts,id,"new-low","final_",List.of(70.0,70.0,20.0)).andExpect(status().isOk());
        assertEquals(216.0,jdbc.queryForObject("select total_score from scores where mode='final_'",Double.class));
    }
    @Test void coachRoutesRejectStudentsAndCrossSessionEditsAndInvalidGroups()throws Exception {
        long id=session(ts,pistol,"S1");
        long a=data(attempt(ts,id,"a","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk())).path("id").asLong();
        var row=editable(id,a);
        mvc.perform(get("/api/coach/training")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coach/training").header("Authorization",ts)).andExpect(status().isForbidden());
        mvc.perform(get("/api/coach/training/"+id).header("Authorization",ts)).andExpect(status().isForbidden());
        correct(ts,id,row,List.of(80.0,80.0,30.0)).andExpect(status().isForbidden());
        correct(tc,id,row,List.of(80.0,80.0,40.1)).andExpect(status().isBadRequest());
        correct(tc,id,row,List.of(80.0,80.0)).andExpect(status().isBadRequest());
        long otherId=session(to,rifle,"S1");
        correct(tc,otherId,row,List.of(80.0,80.0,30.0)).andExpect(status().isNotFound());
        assertEquals(216.0,jdbc.queryForObject("select total_score from score_attempts where id=?",Double.class,a));
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_log where action='CORRECT_TRAINING_SCORE'",Integer.class));
        mvc.perform(get("/api/coach/training").header("Authorization",tc).param("search","duty-student"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.records.length()").value(1)).andExpect(jsonPath("$.data.records[0].member.name").value("student"));
    }
    @Test void correctingActiveMixedRoundsDoesNotPublishEitherModeUntilFinish()throws Exception {
        long id=session(ts,pistol,"S1");
        long a=data(attempt(ts,id,"a","final_",List.of(100.0,100.0,40.0)).andExpect(status().isOk())).path("id").asLong();
        attempt(ts,id,"b","qualifying",Collections.nCopies(6,90.0)).andExpect(status().isOk());
        correct(tc,id,editable(id,a),List.of(80.0,80.0,30.0)).andExpect(status().isOk());
        assertEquals(0,jdbc.queryForObject("select count(*) from scores",Integer.class));
        finish(ts,id);
        assertEquals(190.0,jdbc.queryForObject("select total_score from scores where mode='final_'",Double.class));
        assertEquals(540.0,jdbc.queryForObject("select total_score from scores where mode='qualifying'",Double.class));
    }
    @Test void coachCanCorrectLegacyTotalWithoutInventingRounds()throws Exception {
        long id=session(ts,pistol,"S1");finish(ts,id);
        mvc.perform(post("/api/training/score").header("Authorization",tc).contentType("application/json")
            .content(json.writeValueAsString(Map.of("sessionId",id,"mode","final_","shotScores",Collections.nCopies(24,10.0),"groupSpec",List.of(10,10,4)))))
            .andExpect(status().isOk());
        var row=coachDetail(id).at("/editable/0");assertTrue(row.path("legacy").asBoolean());
        String path="/api/coach/training/"+id+"/legacy-scores/"+row.path("id").asLong();
        String body=json.writeValueAsString(Map.of("expectedState",row.path("state").asText(),"totalScore",200.0,"reason","核对总成绩"));
        mvc.perform(put(path).header("Authorization",ts).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put(path).header("Authorization",tc).contentType("application/json").content(body)).andExpect(status().isOk());
        mvc.perform(put(path).header("Authorization",tr).contentType("application/json").content(body)).andExpect(status().isConflict());
        assertEquals(200.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        assertEquals("[]",jdbc.queryForObject("select shot_scores from scores",String.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from score_attempts",Integer.class));
        attempt(ts,id,"later","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk());
        mvc.perform(put(path).header("Authorization",tc).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
    @Test void concurrentCoachCorrectionsCannotOverwriteOneAnother()throws Exception {
        long id=session(ts,pistol,"S1");
        long a=data(attempt(ts,id,"a","final_",List.of(100.0,100.0,40.0)).andExpect(status().isOk())).path("id").asLong();finish(ts,id);
        var row=editable(id,a);var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var first=pool.submit(()->{gate.await();return correct(tc,id,row,List.of(90.0,90.0,36.0)).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{gate.await();return correct(tr,id,row,List.of(80.0,80.0,30.0)).andReturn().getResponse().getStatus();});
            gate.countDown();assertEquals(Set.of(200,409),Set.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS)));
        }
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where action='CORRECT_TRAINING_SCORE'",Integer.class));
        assertEquals(jdbc.queryForObject("select total_score from score_attempts",Double.class),jdbc.queryForObject("select total_score from scores",Double.class));
    }
    @Test void resumedCorrectionRemovesBadPublishedScoreAndFinishDoesNotRestoreIt()throws Exception {
        long id=session(ts,pistol,"S1");
        long a=data(attempt(ts,id,"a","final_",List.of(100.0,100.0,40.0)).andExpect(status().isOk())).path("id").asLong();finish(ts,id);
        mvc.perform(post("/api/training/resume/"+id).header("Authorization",ts)).andExpect(status().isOk());
        correct(tc,id,editable(id,a),List.of(80.0,80.0,30.0)).andExpect(status().isOk());
        assertEquals(190.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        finish(ts,id);
        assertEquals(190.0,history(ts).at("/personalBests/0/totalScore").asDouble());
    }

    @Test void standaloneLegacyBestRemainsEditableAlongsideLowerSupplementaryRounds()throws Exception {
        long id=session(ts,pistol,"S1");finish(ts,id);
        mvc.perform(post("/api/training/score").header("Authorization",tc).contentType("application/json")
            .content(json.writeValueAsString(Map.of("sessionId",id,"mode","final_","shotScores",Collections.nCopies(24,10.0),"groupSpec",List.of(10,10,4)))))
            .andExpect(status().isOk());
        long a=data(attempt(ts,id,"later","final_",List.of(90.0,90.0,36.0)).andExpect(status().isOk())).path("id").asLong();
        correct(tc,id,editable(id,a),List.of(80.0,80.0,30.0)).andExpect(status().isOk());
        assertEquals(240.0,history(ts).at("/personalBests/0/totalScore").asDouble());
        JsonNode legacy=null;for(var row:coachDetail(id).path("editable"))if(row.path("legacy").asBoolean())legacy=row;
        assertNotNull(legacy);
        mvc.perform(put("/api/coach/training/"+id+"/legacy-scores/"+legacy.path("id").asLong()).header("Authorization",tc).contentType("application/json")
            .content(json.writeValueAsString(Map.of("expectedState",legacy.path("state").asText(),"totalScore",180.0,"reason","旧版高分误录"))))
            .andExpect(status().isOk());
        assertEquals(190.0,history(ts).at("/personalBests/0/totalScore").asDouble());
    }

}
