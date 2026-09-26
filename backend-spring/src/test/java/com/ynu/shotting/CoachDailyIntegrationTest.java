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

@SpringBootTest(properties={"spring.config.name=booking-flow-test", "spring.datasource.url=${test.database.url:jdbc:h2:mem:coach-daily-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}"})
@AutoConfigureMockMvc
@Import(BusinessIntegrationTest.TimeFixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class CoachDailyIntegrationTest {
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
    TrainingSession session(User user,Device device,LocalDateTime start,boolean active) {
        String slot=Arrays.stream(com.ynu.shoting.config.Slot.values()).filter(v->!start.toLocalTime().isBefore(v.getStart()) && start.toLocalTime().isBefore(v.getEnd())).map(com.ynu.shoting.config.Slot::getId).findFirst().orElse("S1");
        var booking=bookings.save(Booking.builder().user(user).device(device).slotDate(start.toLocalDate().toString()).slotId(slot)
                .status(active?Booking.BookingStatus.IN_USE:Booking.BookingStatus.COMPLETED).bookedAt(start.minusHours(1)).build());
        return sessions.save(TrainingSession.builder().booking(booking).user(user).device(device).mode(TrainingSession.SessionMode.final_)
                .startedAt(start).endedAt(active?null:start.plusMinutes(50)).actualDurationMin(active?null:50).build());
    }
    ScoreAttempt round(TrainingSession session,String mode,double total) {
        return attempts.save(ScoreAttempt.builder().session(session).mode(Score.ScoreMode.valueOf(mode)).requestKey(UUID.randomUUID().toString())
                .totalScore(total).groupTotals(mode.equals("final_")?"[90,90,30]":"[90,90,90,90,90,90]").shotScores("[]").groupScores("[]")
                .recordedAt(day.plusDays(1).atTime(9,0)).build());
    }
    void legacy(TrainingSession session,String mode,double total) {
        scores.save(Score.builder().session(session).mode(Score.ScoreMode.valueOf(mode)).totalScore(total).groupScores("[]").shotScores("[]")
                .groupSpec(mode.equals("final_")?"[10,10,4]":"[10,10,10,10,10,10]").shotCount(mode.equals("final_")?24:60).recordedBy(coach).build());
    }
    JsonNode data(ResultActions action)throws Exception {return json.readTree(action.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    JsonNode day()throws Exception {return data(mvc.perform(get("/api/coach/training/days/"+day).header("Authorization",tc)));}
    JsonNode group(JsonNode response,String weapon,String mode) {
        for(var group:response.path("groups"))if(group.path("weapon").asText().equals(weapon)&&group.path("mode").asText().equals(mode))return group;
        throw new AssertionError("Missing group "+weapon+mode);
    }
    @Test void onlyCoachesCanReadDatesAndDayRankings()throws Exception {
        for(String path:List.of("/api/coach/training/days","/api/coach/training/days/"+day)) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization",ta)).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization",tc)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/coach/training/days").header("Authorization",tc).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/coach/training/days/not-a-date").header("Authorization",tc)).andExpect(status().isBadRequest());
    }
    @Test void eachDayRanksOneBestPerMemberWithinWeaponAndModeWithTiesAndUnscoredLast()throws Exception {
        var first=session(a,pistol,day.atTime(8,30),false);round(first,"final_",200);round(first,"final_",215);round(first,"qualifying",570);
        var second=session(a,pistol,day.atTime(14,0),false);round(second,"final_",220);
        var tied=session(b,pistol,day.atTime(10,30),false);round(tied,"final_",220);
        var lower=session(c,pistol,day.atTime(16,0),true);round(lower,"final_",210);
        session(d,pistol,day.atTime(18,0),false);
        var otherWeapon=session(b,rifle,day.atTime(14,0),false);round(otherWeapon,"final_",230);
        var yesterday=session(c,pistol,day.minusDays(1).atTime(23,59),false);round(yesterday,"final_",240);
        var tomorrow=session(d,pistol,day.plusDays(1).atStartOfDay(),false);round(tomorrow,"final_",239);
        var result=day();assertEquals(4,result.path("memberCount").asInt());assertEquals(6,result.path("sessionCount").asInt());
        var members=group(result,"pistol","final_").path("members");assertEquals(4,members.size());
        assertEquals(a.getId(),members.get(0).at("/member/id").asLong());
        assertEquals(220,members.get(0).path("bestTotal").asDouble());assertEquals(3,members.get(0).path("rounds").asInt());assertEquals(2,members.get(0).path("sessions").size());
        assertEquals(List.of(1,1,3),List.of(members.get(0).path("rank").asInt(),members.get(1).path("rank").asInt(),members.get(2).path("rank").asInt()));
        assertTrue(members.get(3).path("rank").isNull());assertTrue(members.get(3).path("bestTotal").isNull());
        assertEquals(570,group(result,"pistol","qualifying").at("/members/0/bestTotal").asDouble());
        assertEquals(230,group(result,"rifle","final_").at("/members/0/bestTotal").asDouble());
        assertTrue(members.get(2).at("/sessions/0/session/endedAt").isNull());
    }
    @Test void lateEntriesRemainOnTrainingStartDateAndLegacyTotalDoesNotInventRounds()throws Exception {
        var old=session(a,pistol,day.atTime(23,50),false);legacy(old,"final_",225);
        var mixed=session(b,pistol,day.atTime(14,0),false);round(mixed,"final_",210);legacy(mixed,"final_",230);
        var result=day();var rows=group(result,"pistol","final_").path("members");
        assertEquals(230,rows.get(0).path("bestTotal").asDouble());assertEquals(1,rows.get(0).path("rounds").asInt());
        assertEquals(225,rows.get(1).path("bestTotal").asDouble());assertEquals(0,rows.get(1).path("rounds").asInt());
        var next=data(mvc.perform(get("/api/coach/training/days/"+day.plusDays(1)).header("Authorization",tc)));
        assertEquals(0,next.path("sessionCount").asInt());
        var detail=data(mvc.perform(get("/api/coach/training/"+old.getId()).header("Authorization",tc)));
        assertEquals("2026-09-25T23:50:00",detail.at("/results/session/startedAt").asText());
        assertEquals("2026-09-26T00:40:00",detail.at("/results/session/endedAt").asText());
        assertEquals(0,detail.at("/results/attempts").size());assertTrue(detail.at("/editable/0/legacy").asBoolean());
    }
    @Test void dayCardsPaginateWholeDaysAndDayRankingsIncludeMoreThanThirtySessions()throws Exception {
        for(int i=0;i<35;i++) {final int index=i;var student=new TransactionTemplate(transactions).execute(t->user("分页学员"+index,User.Role.student));var s=session(student,pistol,day.atTime(8,30).plusMinutes(i),false);round(s,"final_",180+i);}
        for(int i=1;i<=30;i++)session(b,rifle,day.minusDays(i).atTime(8,30),false);
        var first=data(mvc.perform(get("/api/coach/training/days").header("Authorization",tc)));
        assertEquals(30,first.path("days").size());assertTrue(first.path("hasMore").asBoolean());
        assertEquals(day.toString(),first.at("/days/0/date").asText());assertEquals(35,first.at("/days/0/sessions").asInt());assertEquals(35,first.at("/days/0/members").asInt());
        var second=data(mvc.perform(get("/api/coach/training/days").header("Authorization",tc).param("page","1")));
        assertEquals(1,second.path("days").size());assertFalse(second.path("hasMore").asBoolean());
        var rows=group(day(),"pistol","final_").path("members");assertEquals(35,rows.size());assertEquals(214,rows.get(0).path("bestTotal").asDouble());
    }
    @Test void correctingOneRoundReordersOnlyItsOriginalDayRanking()throws Exception {
        var first=session(a,pistol,day.atTime(8,30),false);var target=round(first,"final_",220);
        var second=session(b,pistol,day.atTime(10,30),false);round(second,"final_",210);
        var yesterday=session(a,pistol,day.minusDays(1).atTime(8,30),false);round(yesterday,"final_",230);
        var detail=data(mvc.perform(get("/api/coach/training/"+first.getId()).header("Authorization",tc)));
        mvc.perform(put("/api/coach/training/"+first.getId()+"/attempts/"+target.getId()).header("Authorization",tc).contentType("application/json")
                .content(json.writeValueAsString(Map.of("expectedState",detail.at("/editable/0/state").asText(),"reason","核对原始分组成绩","groupTotals",List.of(80,80,30)))))
                .andExpect(status().isOk());
        var rows=group(day(),"pistol","final_").path("members");assertEquals(b.getId(),rows.get(0).at("/member/id").asLong());assertEquals(190,rows.get(1).path("bestTotal").asDouble());
        var previous=data(mvc.perform(get("/api/coach/training/days/"+day.minusDays(1)).header("Authorization",tc)));
        assertEquals(230,group(previous,"pistol","final_").at("/members/0/bestTotal").asDouble());
    }
}
