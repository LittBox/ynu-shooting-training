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
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.name=booking-flow-test", "spring.datasource.url=${test.database.url:jdbc:h2:mem:coach-history-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}"})
@AutoConfigureMockMvc
@Import(BusinessIntegrationTest.TimeFixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CoachHistoricalIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired BusinessIntegrationTest.MutableClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ProfileRepository profiles;
    @Autowired DeviceRepository devices;
    @Autowired BookingRepository bookings;
    @Autowired TrainingSessionRepository sessions;
    @Autowired ScoreAttemptRepository attempts;
    @Autowired ScoreRepository scores;
    @Autowired JwtTokenProvider tokens;
    @Autowired PlatformTransactionManager transactions;
    User coach, a, b, c, d;
    Device pistol, rifle;
    String tc, ta;
    final LocalDate day=LocalDate.of(2026,9,25);

    @BeforeEach void setup() {
        clock.set("2026-09-26T09:00:00+08:00");
        new TransactionTemplate(transactions).executeWithoutResult(t -> {
            for(String table:List.of("wechat_reminders","score_attempts","scores","training_sessions","cancellation_log","no_show_records","bookings","admin_schedules","user_availability","audit_log","profiles","users","devices"))jdbc.update("delete from "+table);
            coach=user("教练",User.Role.admin);a=user("学员甲",User.Role.student);b=user("学员乙",User.Role.student);c=user("学员丙",User.Role.student);d=user("学员丁",User.Role.student);
            pistol=devices.save(Device.builder().name("气手枪 1 号").type(Device.DeviceType.pistol).build());
            rifle=devices.save(Device.builder().name("气步枪 1 号").type(Device.DeviceType.rifle).build());
        });
        tc="Bearer "+tokens.generateToken(coach);ta="Bearer "+tokens.generateToken(a);
    }
    User user(String name,User.Role role) {
        var user=users.save(User.builder().openid("daily-"+name).nickname(name).role(role).profileStatus(User.ProfileStatus.completed).build());
        user.setProfile(profiles.save(Profile.builder().user(user).realName(name).studentNo("daily-"+name).phone("13800000000").gender("M").build()));return user;
    }
    Map<String,Object> body() {
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("requestKey",UUID.randomUUID().toString());body.put("userId",a.getId());body.put("weapon","pistol");
        body.put("startedAt","2026-09-25T23:30:00");body.put("endedAt","2026-09-26T00:30:00");
        body.put("rounds",List.of(Map.of("mode","final_","groupTotals",List.of(90,90,30)),
                Map.of("mode","qualifying","groupTotals",List.of(90,90,90,90,90,90)),
                Map.of("mode","final_","groupTotals",List.of(95,95,35))));
        return body;
    }
    ResultActions create(Map<String,Object> body,String token)throws Exception {
        return mvc.perform(post("/api/coach/training/history").header("Authorization",token).contentType("application/json").content(json.writeValueAsString(body)));
    }
    JsonNode data(ResultActions action)throws Exception {
        return json.readTree(action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    @Test void onlyCoachCanSearchAndCreateIncludingMembersWithoutPriorTraining()throws Exception {
        mvc.perform(get("/api/coach/training/members").param("search","学员")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/coach/training/members").param("search","学员").header("Authorization",ta)).andExpect(status().isForbidden());
        create(body(),ta).andExpect(status().isForbidden());
        var found=data(mvc.perform(get("/api/coach/training/members").param("search","学员甲").header("Authorization",tc)));
        assertEquals(1,found.path("members").size());assertEquals(a.getId(),found.at("/members/0/id").asLong());
        assertFalse(found.at("/members/0").has("phone"));
        var byNo=data(mvc.perform(get("/api/coach/training/members").param("search","daily-学员乙").header("Authorization",tc)));
        assertEquals(b.getId(),byNo.at("/members/0/id").asLong());
        var blank=data(mvc.perform(get("/api/coach/training/members").header("Authorization",tc)));
        assertEquals(0,blank.path("members").size());assertEquals(0,sessions.count());
    }
    @Test void historicalRoundsJoinOriginalDayPersonalHistoryAndPublishedRankingsWithoutBookings()throws Exception {
        pistol.setStatus(Device.DeviceStatus.IN_USE);devices.save(pistol);
        var detail=data(create(body(),tc));long id=detail.at("/results/session/id").asLong();
        assertTrue(detail.at("/results/session/historical").asBoolean());assertTrue(detail.at("/results/session/bookingId").isNull());
        assertEquals("教练",detail.at("/results/session/recordedByName").asText());
        assertEquals(60,detail.at("/results/session/actualDurationMin").asInt());assertEquals(3,detail.at("/results/attempts").size());
        assertFalse(detail.at("/results/canResume").asBoolean());
        var day=data(mvc.perform(get("/api/coach/training/days/2026-09-25").header("Authorization",tc)));
        assertEquals(1,day.path("sessionCount").asInt());assertEquals(2,day.path("groups").size());
        assertEquals(225,day.at("/groups/0/members/0/bestTotal").asDouble());assertEquals(2,day.at("/groups/0/members/0/rounds").asInt());
        var cards=data(mvc.perform(get("/api/coach/training/days").header("Authorization",tc)));
        assertEquals("2026-09-25",cards.at("/days/0/date").asText());
        var today=data(mvc.perform(get("/api/coach/training/days/2026-09-26").header("Authorization",tc)));assertEquals(0,today.path("sessionCount").asInt());
        var history=data(mvc.perform(get("/api/training/records/me").header("Authorization",ta)));assertEquals(2,history.path("sessions").size());
        assertEquals(0,history.path("unscoredSessions").size());
        var rank=data(mvc.perform(get("/api/leaderboard").param("weapon","PISTOL").param("event","FINAL")));assertTrue(rank.toString().contains("225"));
        mvc.perform(get("/api/training/"+id+"/results").header("Authorization",ta)).andExpect(status().isOk());
        String other="Bearer "+tokens.generateToken(b);
        mvc.perform(get("/api/training/"+id+"/results").header("Authorization",other)).andExpect(status().isForbidden());
        assertEquals(0,bookings.count());assertEquals(Device.DeviceStatus.IN_USE,devices.findById(pistol.getId()).orElseThrow().getStatus());
        assertEquals(0,sessions.countByEndedAtIsNull());assertEquals(1,jdbc.queryForObject("select count(*) from audit_log where action='CREATE_HISTORICAL_TRAINING'",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from wechat_reminders",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from admin_schedules",Integer.class));
    }
    @Test void invalidTimesRoundsAndStudentsLeaveNoPartialRows()throws Exception {
        List<Map<String,Object>> invalid=new ArrayList<>();
        for(var patch:List.of(Map.of("endedAt","2026-09-25T23:30:00"),Map.of("endedAt","2026-09-27T00:30:00"),Map.of("weapon","invalid"),
                Map.of("rounds",List.of()),Map.of("rounds",List.of(Map.of("mode","bad","groupTotals",List.of(1,2,3)))),
                Map.of("rounds",List.of(Map.of("mode","final_","groupTotals",List.of(100.1,90,30)))),
                Map.of("rounds",List.of(Map.of("mode","qualifying","groupTotals",List.of(90,90,30)))))) {
            var value=body();value.putAll(patch);invalid.add(value);
        }
        var nullRound=body();nullRound.put("rounds",Arrays.asList((Object)null));invalid.add(nullRound);
        var nullTime=body();nullTime.remove("startedAt");invalid.add(nullTime);
        for(var value:invalid)create(value,tc).andExpect(status().isBadRequest());
        var missing=body();missing.put("userId",Long.MAX_VALUE);create(missing,tc).andExpect(status().isNotFound());
        assertEquals(0,sessions.count());assertEquals(0,attempts.count());assertEquals(0,scores.count());
    }
    @Test void retriesAreIdempotentEvenAfterCorrectionAndConflictingPayloadIsRejected()throws Exception {
        var body=body();var first=data(create(body,tc));long id=first.at("/results/session/id").asLong();
        assertEquals(id,data(create(body,tc)).at("/results/session/id").asLong());
        var edit=first.at("/editable/2");
        var corrected=data(mvc.perform(put("/api/coach/training/"+id+"/attempts/"+edit.path("id").asLong()).header("Authorization",tc).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedState",edit.path("state").asText(),"reason","核对历史登记","groupTotals",List.of(80,80,30))))));
        assertEquals(210,corrected.at("/results/modeResults/0/bestTotal").asDouble());
        assertEquals(540,corrected.at("/results/modeResults/1/bestTotal").asDouble());
        assertEquals(id,data(create(body,tc)).at("/results/session/id").asLong());
        body.put("weapon","rifle");create(body,tc).andExpect(status().isConflict());
        assertEquals(1,sessions.count());assertEquals(3,attempts.count());assertEquals(2,scores.count());
    }
    @Test void studentsCannotExtendHistoricalRoundsOrResumeThem()throws Exception {
        long id=data(create(body(),tc)).at("/results/session/id").asLong();
        mvc.perform(post("/api/training/"+id+"/attempts").header("Authorization",ta).contentType("application/json")
                .content(json.writeValueAsString(Map.of("requestKey","new-round","mode","final_","groupTotals",List.of(100,100,40))))).andExpect(status().isConflict());
        mvc.perform(post("/api/training/resume/"+id).header("Authorization",ta)).andExpect(status().is4xxClientError());
        mvc.perform(post("/api/training/finish/"+id).header("Authorization",ta)).andExpect(status().is4xxClientError());
        assertEquals(3,attempts.count());assertEquals(0,bookings.count());
    }
    @Test void simultaneousRetriesCreateExactlyOneSession()throws Exception {
        var payload=body();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> call=()->{gate.await();return data(create(payload,tc)).at("/results/session/id").asLong();};
            var one=executor.submit(call);var two=executor.submit(call);gate.countDown();
            assertEquals(one.get(15,java.util.concurrent.TimeUnit.SECONDS),two.get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,sessions.count());assertEquals(3,attempts.count());
    }
}
