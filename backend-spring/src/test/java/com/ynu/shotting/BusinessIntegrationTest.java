package com.ynu.shoting;
import com.fasterxml.jackson.databind.*;
import com.ynu.shoting.config.Slot;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.JwtTokenProvider;
import com.ynu.shoting.service.NoShowService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={"spring.config.name=booking-flow-test", "auth.coach-invite-code=test-coach-invitation-2026", "spring.datasource.url=${test.database.url:jdbc:h2:mem:business-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}"})
@AutoConfigureMockMvc @Import(BusinessIntegrationTest.TimeFixture.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BusinessIntegrationTest {
    static class MutableClock extends Clock {
        volatile Instant instant=Instant.parse("2026-09-25T00:00:00Z");
        public ZoneId getZone(){return ZoneId.of("Asia/Shanghai");}
        public Clock withZone(ZoneId zone){return Clock.fixed(instant,zone);}
        public Instant instant(){return instant;}
        void set(String value){instant=OffsetDateTime.parse(value).toInstant();}
    }
    @TestConfiguration static class TimeFixture { @Bean @Primary MutableClock testClock(){return new MutableClock();} }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc; @Autowired UserRepository users; @Autowired ProfileRepository profiles;
    @Autowired DeviceRepository devices; @Autowired BookingRepository bookings; @Autowired AdminScheduleRepository schedules;
    @Autowired TrainingSessionRepository sessions; @Autowired ScoreRepository scores; @Autowired NoShowRecordRepository noShows;
    @Autowired CancellationLogRepository cancellations; @Autowired JwtTokenProvider tokens; @Autowired NoShowService expiry;
    @Autowired AuditLogRepository auditLogs;
    @Autowired PlatformTransactionManager transactions;
    User a,b,admin; Device pistol,rifle; String ta,tb,tm; final String date="2026-09-25";
    @BeforeEach void setup() {
        clock.set("2026-09-25T08:00:00+08:00");
        new TransactionTemplate(transactions).executeWithoutResult(t -> {
            for(String table:List.of("score_attempts","scores","training_sessions","cancellation_log","no_show_records","bookings","admin_schedules","user_availability","audit_log","profiles","users","devices")) jdbc.update("delete from "+table);
            a=user("a",User.Role.student,"M"); b=user("b",User.Role.student,"F"); admin=user("admin",User.Role.admin,"U");
            pistol=devices.save(Device.builder().name("联调手枪").type(Device.DeviceType.pistol).build());
            rifle=devices.save(Device.builder().name("联调步枪").type(Device.DeviceType.rifle).build());
            for(int i=0;i<=7;i++) for(Slot slot:Slot.values())
                schedules.save(AdminSchedule.builder().admin(admin).slotDate(LocalDate.parse(date).plusDays(i).toString()).slotId(slot.getId()).build());
        });
        ta="Bearer "+tokens.generateToken(a); tb="Bearer "+tokens.generateToken(b); tm="Bearer "+tokens.generateToken(admin);
    }
    User user(String key,User.Role role,String gender) {
        User u=users.save(User.builder().openid("fixture-"+key).nickname("昵称"+key).role(role).profileStatus(User.ProfileStatus.completed).build());
        Profile p=profiles.save(Profile.builder().user(u).realName("私密姓名"+key).studentNo("PRIVATE-"+key).phone("13800000000").gender(gender).build());
        u.setProfile(p); return u;
    }
    ResultActions postJson(String path,String token,Object body)throws Exception {
        var req=post(path).contentType("application/json").content(json.writeValueAsString(body));
        if(token!=null)req.header("Authorization",token); return mvc.perform(req);
    }
    JsonNode data(ResultActions action)throws Exception {return json.readTree(action.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");}
    Map<String,Object> payload(Device d,String day,String slot){return Map.of("deviceId",d.getId(),"slotDate",day,"slotId",slot,"availabilityConfirmed",true);}
    long book(String token,Device d,String day,String slot)throws Exception {return data(postJson("/api/bookings",token,payload(d,day,slot)).andExpect(status().isOk())).path("id").asLong();}
    void arriveCoach() throws Exception {
        long id=schedules.findByAdminIdAndSlotDateAndSlotId(admin.getId(),date,"S1").orElseThrow().getId();
        mvc.perform(post("/api/admin/schedules/"+id+"/arrive").header("Authorization",tm)).andExpect(status().isOk());
    }
    void checkIn(long id)throws Exception {mvc.perform(post("/api/bookings/"+id+"/checkin").header("Authorization",ta)).andExpect(status().isOk());}
    long complete(String mode)throws Exception {
        long id=book(ta,pistol,date,"S1"); checkIn(id); arriveCoach();
        long sid=data(mvc.perform(post("/api/training/start/"+id).param("mode",mode).header("Authorization",ta)).andExpect(status().isOk())).path("id").asLong();
        mvc.perform(post("/api/training/finish/"+sid).header("Authorization",ta)).andExpect(status().isOk()).andExpect(jsonPath("$.data.actualDurationMin").value(1));return sid;
    }
    Map<String,Object> score(long id,String mode,int count,List<Integer> groups){return Map.of("sessionId",id,"mode",mode,"shotScores",Collections.nCopies(count,9.0),"groupSpec",groups);}
    @Test void authenticationValidationAndOwnership()throws Exception {
        postJson("/api/auth/login",null,Map.of("code"," ","mode","mock")).andExpect(status().isBadRequest());
        postJson("/api/bookings",null,payload(pistol,date,"S1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/devices").header("Authorization",ta)).andExpect(status().isForbidden());
        long id=book(ta,pistol,date,"S1");
        for(String action:List.of("cancel","checkin")) mvc.perform(post("/api/bookings/"+id+"/"+action).header("Authorization",tb)).andExpect(status().isForbidden());
        mvc.perform(get("/api/bookings/"+id).header("Authorization",tb)).andExpect(status().isForbidden());
        postJson("/api/bookings",ta,Map.of()).andExpect(status().isBadRequest());
        postJson("/api/bookings",ta,payload(pistol,"not-date","S1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/bookings/slots").param("date","broken")).andExpect(status().isBadRequest());
    }
    @Test void profileCanBeResavedButStudentNumberIsImmutableAndUnique()throws Exception {
        postJson("/api/auth/profile",ta,Map.of("realName","新姓名","studentNo","PRIVATE-a","phone","13800000001","gender","M")).andExpect(status().isOk());
        postJson("/api/auth/profile",ta,Map.of("realName","新姓名","studentNo","changed","phone","13800000001")).andExpect(status().isBadRequest());
        String token="Bearer "+data(postJson("/api/auth/login",null,Map.of("mode","mock","code","new-student")).andExpect(status().isOk())).path("token").asText();
        postJson("/api/auth/profile",token,Map.of("realName","测试","studentNo","PRIVATE-a","phone","13800000000","gender","M")).andExpect(status().isConflict());
        postJson("/api/auth/profile",token,Map.of("realName","测试","studentNo","new","phone","bad")).andExpect(status().isBadRequest());
        postJson("/api/bookings",token,payload(pistol,date,"S1")).andExpect(status().isBadRequest());
    }
    @Test void genderIsRequiredAndCannotBeUnknown() throws Exception {
        var profile = new HashMap<String,Object>(Map.of("realName","测试", "studentNo","PRIVATE-a", "phone","13800000000"));
        postJson("/api/auth/profile",ta,profile).andExpect(status().isBadRequest());
        postJson("/api/auth/profile",null,profile).andExpect(status().isBadRequest());
        for (String invalid : List.of("", "U", "unknown")) {
            profile.put("gender", invalid);
            postJson("/api/auth/profile",ta,profile).andExpect(status().isBadRequest());
        }
        profile.put("gender", null);
        postJson("/api/auth/profile",ta,profile).andExpect(status().isBadRequest());
        profile.put("gender", "F");
        postJson("/api/auth/profile",ta,profile).andExpect(status().isOk()).andExpect(jsonPath("$.data.gender").value("F"));
    }

    @Test void legacyUnknownGenderMustBeCompletedAndOwnProfileCanBeEdited() throws Exception {
        jdbc.update("update profiles set gender='U' where user_id=?", a.getId());
        jdbc.update("update users set openid='mock:legacy-profile' where id=?", a.getId());
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileStatus").value("pending"))
                .andExpect(jsonPath("$.data.realName").value("私密姓名a"))
                .andExpect(jsonPath("$.data.studentNo").value("PRIVATE-a"))
                .andExpect(jsonPath("$.data.openid").doesNotExist());
        postJson("/api/auth/login",null,Map.of("code","legacy-profile","mode","mock"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileStatus").value("pending"));
        postJson("/api/bookings",ta,payload(pistol,date,"S1")).andExpect(status().isBadRequest());
        postJson("/api/auth/profile",ta,Map.of("realName","更新姓名","studentNo","PRIVATE-a","phone","13800000001","gender","M"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileStatus").value("completed"));
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.realName").value("更新姓名"))
                .andExpect(jsonPath("$.data.phone").value("13800000001"))
                .andExpect(jsonPath("$.data.gender").value("M"));
    }

    @Test void coachAuthorizationRefreshesWithExistingTokenAndRetainsStudentBooking() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(jsonPath("$.data.role").value("student"));
        mvc.perform(get("/api/admin/duty").header("Authorization",ta).param("date",date)).andExpect(status().isForbidden());
        jdbc.update("update users set role='admin' where id=?",a.getId());
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("admin"));
        mvc.perform(post("/api/admin/schedules").header("Authorization",ta).param("adminId",a.getId().toString())
                .param("slotDate",date).param("slotId","S2")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminName").value("私密姓名a"));
        mvc.perform(get("/api/admin/duty").header("Authorization",ta).param("date",date)).andExpect(status().isOk());
        postJson("/api/bookings",ta,payload(pistol,date,"S1")).andExpect(status().isOk());
        jdbc.update("update users set role='student' where id=?",a.getId());
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(jsonPath("$.data.role").value("student"));
        mvc.perform(get("/api/admin/duty").header("Authorization",ta).param("date",date)).andExpect(status().isForbidden());
    }

    @Test void suppliedRoleDoesNotGrantCoachAccess() throws Exception {
        postJson("/api/auth/login",null,Map.of("code","not-a-coach","mode","mock","role","admin"))
                .andExpect(status().isBadRequest());
        postJson("/api/auth/login",null,Map.of("code","not-a-coach","mode","mock"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("student"));
        postJson("/api/auth/profile",ta,Map.of("realName","测试","studentNo","PRIVATE-a","phone","13800000000","gender","M","role","admin"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("student"));
    }

    @Test void invitationActivatesOnlyAuthenticatedAccountAndIsIdempotent() throws Exception {
        var invitation = Map.of("inviteCode","test-coach-invitation-2026");
        postJson("/api/auth/coach-activation",null,invitation).andExpect(status().isUnauthorized());
        postJson("/api/auth/coach-activation",ta,Map.of("inviteCode","wrong-invitation"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").header("Authorization",ta)).andExpect(jsonPath("$.data.role").value("student"));
        JsonNode activated = data(postJson("/api/auth/coach-activation",ta,invitation).andExpect(status().isOk()));
        assertEquals("admin",activated.path("role").asText());
        assertEquals("私密姓名a",activated.path("realName").asText());
        assertFalse(activated.toString().contains("test-coach-invitation-2026"));
        postJson("/api/auth/coach-activation",ta,invitation).andExpect(status().isOk());
        assertEquals(1,auditLogs.findAll().stream().filter(log -> log.getAction().equals("ACTIVATE_COACH")).count());
        assertEquals(User.Role.student,users.findById(b.getId()).orElseThrow().getRole());
        mvc.perform(get("/api/admin/duty").header("Authorization",ta).param("date",date)).andExpect(status().isOk());
    }

    @Test void invitationRequiresCompleteProfileAndRejectsEmptyCode() throws Exception {
        postJson("/api/auth/coach-activation",ta,Map.of("inviteCode"," ")).andExpect(status().isBadRequest());
        jdbc.update("update profiles set gender='U' where user_id=?",a.getId());
        postJson("/api/auth/coach-activation",ta,Map.of("inviteCode","test-coach-invitation-2026"))
                .andExpect(status().isBadRequest());
        assertEquals(User.Role.student,users.findById(a.getId()).orElseThrow().getRole());
    }

    @Test void invitationAttemptsAreLimitedPerAccountAndRecoverAfterWindow() throws Exception {
        for (int i=0;i<5;i++) postJson("/api/auth/coach-activation",ta,Map.of("inviteCode","wrong"))
                .andExpect(status().isForbidden());
        postJson("/api/auth/coach-activation",ta,Map.of("inviteCode","test-coach-invitation-2026"))
                .andExpect(status().isTooManyRequests());
        postJson("/api/auth/coach-activation",tb,Map.of("inviteCode","test-coach-invitation-2026"))
                .andExpect(status().isOk());
        clock.set("2026-09-25T08:16:00+08:00");
        postJson("/api/auth/coach-activation",ta,Map.of("inviteCode","test-coach-invitation-2026"))
                .andExpect(status().isOk());
    }

    @Test void threeWayAvailabilityAndServiceableDeviceAreEnforced()throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(t -> schedules.deleteAll());
        JsonNode slots=data(mvc.perform(get("/api/bookings/slots").param("date",date)).andExpect(status().isOk()));
        for(JsonNode slot:slots){assertEquals(0,slot.path("available").asInt());assertFalse(slot.path("staffed").asBoolean());}
        postJson("/api/bookings",ta,payload(pistol,date,"S1")).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/schedules").header("Authorization",tm).param("adminId",admin.getId().toString()).param("slotDate",date).param("slotId","S1")).andExpect(status().isOk());
        var unconfirmed=new HashMap<>(payload(pistol,date,"S1"));unconfirmed.remove("availabilityConfirmed");
        postJson("/api/bookings",ta,unconfirmed).andExpect(status().isConflict());
        mvc.perform(put("/api/users/me/availability").header("Authorization",ta).contentType("application/json").content(json.writeValueAsString(Map.of("date",date,"slotIds",List.of("S1"))))).andExpect(status().isOk());
        postJson("/api/bookings",ta,unconfirmed).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/schedules/"+schedules.findAll().get(0).getId()).header("Authorization",tm)).andExpect(status().isConflict());
        mvc.perform(patch("/api/admin/devices/"+rifle.getId()+"/status").header("Authorization",tm).param("status","MAINTENANCE")).andExpect(status().isOk());
        postJson("/api/bookings",tb,payload(rifle,date,"S2")).andExpect(status().isConflict());
    }
    @Test void advanceWindowAndElapsedSlots()throws Exception {
        book(ta,pistol,"2026-10-02","S1");
        postJson("/api/bookings",ta,payload(pistol,"2026-10-03","S1")).andExpect(status().isBadRequest());
        postJson("/api/bookings",ta,payload(pistol,"2026-09-24","S1")).andExpect(status().isBadRequest());
        clock.set("2026-09-25T08:30:00+08:00");postJson("/api/bookings",ta,payload(pistol,date,"S1")).andExpect(status().isBadRequest());
    }
    @Test void completedBookingsReleaseDeviceButCountTowardDailyAndWeeklyCaps()throws Exception {
        complete("final_");
        JsonNode slots=data(mvc.perform(get("/api/bookings/slots").param("date",date)).andExpect(status().isOk()));
        for(JsonNode slot:slots)if(slot.path("id").asText().equals("S1") && slot.path("deviceId").asLong()==pistol.getId())assertEquals(1,slot.path("available").asInt());
        postJson("/api/bookings",tb,payload(pistol,date,"S1")).andExpect(status().isOk());
        book(ta,pistol,date,"S2");book(ta,pistol,date,"S3");
        postJson("/api/bookings",ta,payload(pistol,date,"S4")).andExpect(status().isTooManyRequests());
        for(String day:List.of("2026-09-28","2026-09-29","2026-09-30"))for(String slot:List.of("S1","S2","S3"))book(ta,pistol,day,slot);
        book(ta,pistol,"2026-10-01","S1");postJson("/api/bookings",ta,payload(pistol,"2026-10-01","S2")).andExpect(status().isTooManyRequests());
    }
    @Test void cancellationReleasesTheSlotAndCannotCancelTraining()throws Exception {
        long id=book(ta,pistol,date,"S1");mvc.perform(post("/api/bookings/"+id+"/cancel").header("Authorization",ta)).andExpect(status().isOk());book(tb,pistol,date,"S1");
        mvc.perform(post("/api/bookings/"+id+"/cancel").header("Authorization",ta)).andExpect(status().isConflict());
        long other=book(ta,rifle,date,"S1");checkIn(other);mvc.perform(post("/api/bookings/"+other+"/cancel").header("Authorization",ta)).andExpect(status().isConflict());assertEquals(1,cancellations.count());
        mvc.perform(post("/api/admin/bookings/"+other+"/cancel").header("Authorization",tm)).andExpect(status().isOk());
        assertEquals(Booking.BookingStatus.CANCELLED,bookings.findById(other).orElseThrow().getStatus());
    }
    @Test void checkinBoundariesAndExpiryAreCommitted()throws Exception {
        clock.set("2026-09-25T07:59:59.999+08:00");long id=book(ta,pistol,date,"S1");mvc.perform(post("/api/bookings/"+id+"/checkin").header("Authorization",ta)).andExpect(status().isBadRequest());
        clock.set("2026-09-25T08:00:00+08:00");checkIn(id);long last=book(tb,rifle,date,"S1");clock.set("2026-09-25T08:40:00+08:00");
        mvc.perform(post("/api/bookings/"+last+"/checkin").header("Authorization",tb)).andExpect(status().isOk());
        clock.set("2026-09-25T08:00:00+08:00");long late=book(ta,pistol,date,"S2");clock.set("2026-09-25T10:40:00.001+08:00");
        mvc.perform(post("/api/bookings/"+late+"/checkin").header("Authorization",ta)).andExpect(status().isGone());
        assertEquals(Booking.BookingStatus.NO_SHOW,bookings.findById(late).orElseThrow().getStatus());assertEquals(1,noShows.count());expiry.expire(late);assertEquals(1,noShows.count());
    }
    @Test void sweeperIncludesOldDaysAndPreservesExactDeadline()throws Exception {
        long id=book(ta,pistol,date,"S1");clock.set("2026-09-25T08:40:00+08:00");expiry.expire(id);assertEquals(Booking.BookingStatus.BOOKED,bookings.findById(id).orElseThrow().getStatus());
        clock.set("2026-09-26T00:01:00+08:00");assertTrue(bookings.findOverdueCandidates("2026-09-26").contains(id));expiry.expire(id);assertEquals(Booking.BookingStatus.NO_SHOW,bookings.findById(id).orElseThrow().getStatus());
    }
    @Test void trainingStateDeviceStateAndScorePermissions()throws Exception {
        long id=book(ta,pistol,date,"S1");mvc.perform(post("/api/training/start/"+id).header("Authorization",ta)).andExpect(status().isConflict());checkIn(id);arriveCoach();
        long sid=data(mvc.perform(post("/api/training/start/"+id).header("Authorization",ta)).andExpect(status().isOk())).path("id").asLong();
        assertEquals(Device.DeviceStatus.IN_USE,devices.findById(pistol.getId()).orElseThrow().getStatus());
        mvc.perform(get("/api/bookings/me").header("Authorization",ta)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].sessionId").value(sid));
        postJson("/api/training/score",tm,score(sid,"final_",24,List.of(10,10,4))).andExpect(status().isConflict());
        mvc.perform(post("/api/training/finish/"+sid).header("Authorization",tb)).andExpect(status().isForbidden());
        mvc.perform(post("/api/training/finish/"+sid).header("Authorization",ta)).andExpect(status().isOk()).andExpect(jsonPath("$.data.actualDurationMin").value(1));
        assertEquals(Device.DeviceStatus.IDLE,devices.findById(pistol.getId()).orElseThrow().getStatus());mvc.perform(post("/api/training/finish/"+sid).header("Authorization",ta)).andExpect(status().isConflict());
        postJson("/api/training/score",ta,score(sid,"final_",24,List.of(10,10,4))).andExpect(status().isForbidden());
        postJson("/api/training/score",tm,score(sid,"final_",24,List.of(8,8,8))).andExpect(status().isBadRequest());
        postJson("/api/training/score",tm,score(sid,"final_",3,List.of(1,1,1))).andExpect(status().isBadRequest());
        var invalid=new HashMap<>(score(sid,"final_",24,List.of(10,10,4)));invalid.put("shotScores",Collections.nCopies(24,11));postJson("/api/training/score",tm,invalid).andExpect(status().isBadRequest());
        postJson("/api/training/score",tm,score(sid,"final_",24,List.of(10,10,4))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalScore").value(216.0)).andExpect(jsonPath("$.data.groupScores").value("[9.0,9.0,9.0]"));
        postJson("/api/training/score",tm,score(sid,"final_",24,List.of(10,10,4))).andExpect(status().isConflict());
        mvc.perform(get("/api/training/me").header("Authorization",ta)).andExpect(status().isOk()).andExpect(jsonPath("$.data[0].user").doesNotExist());
    }
    @Test void qualificationUsesSixtyShotsAndSeparateBoard()throws Exception {
        long sid=complete("qualifying");postJson("/api/training/score",tm,score(sid,"final_",24,List.of(10,10,4))).andExpect(status().isOk());
        postJson("/api/training/score",tm,score(sid,"qualifying",60,List.of(10,10,10,10,10,10))).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalScore").value(540.0));
        mvc.perform(get("/api/leaderboard").param("weapon","PISTOL").param("event","QUALIFICATION")).andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].score").value(540.0));
        mvc.perform(get("/api/leaderboard").param("weapon","PISTOL").param("event","FINAL")).andExpect(status().isOk()).andExpect(jsonPath("$.data.rows[0].score").value(216.0));
    }
    @Test void twoStudentsCannotBookSameResourceConcurrently()throws Exception {
        assertEquals(List.of(200,409),race(() -> postJson("/api/bookings",ta,payload(pistol,date,"S1")).andReturn().getResponse().getStatus(),()->postJson("/api/bookings",tb,payload(pistol,date,"S1")).andReturn().getResponse().getStatus()));assertEquals(1,bookings.count());
    }
    @Test void oneStudentCannotBookTwoDevicesConcurrently()throws Exception {
        assertEquals(List.of(200,409),race(() -> postJson("/api/bookings",ta,payload(pistol,date,"S1")).andReturn().getResponse().getStatus(),()->postJson("/api/bookings",ta,payload(rifle,date,"S1")).andReturn().getResponse().getStatus()));
    }
    @Test void concurrentDifferentSlotsCannotExceedDailyQuota()throws Exception {
        book(ta,pistol,date,"S1");book(ta,pistol,date,"S2");assertEquals(List.of(200,429),race(() -> postJson("/api/bookings",ta,payload(pistol,date,"S3")).andReturn().getResponse().getStatus(),()->postJson("/api/bookings",ta,payload(rifle,date,"S4")).andReturn().getResponse().getStatus()));
    }
    List<Integer> race(Callable<Integer> one,Callable<Integer> two)throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);List<Future<Integer>> results=new ArrayList<>();
            for(var task:List.of(one,two))results.add(pool.submit(()->{ready.countDown();go.await();return task.call();}));
            assertTrue(ready.await(3,TimeUnit.SECONDS));go.countDown();var codes=new ArrayList<Integer>();for(var r:results)codes.add(r.get(10,TimeUnit.SECONDS));Collections.sort(codes);return codes;
        }
    }

    void historicalScore(User user,Device device,Score.ScoreMode mode,String at,double total) {
        new TransactionTemplate(transactions).executeWithoutResult(t -> {
            LocalDateTime time=LocalDateTime.parse(at);
            Booking booking=bookings.save(Booking.builder().user(user).device(device).slotDate(time.toLocalDate().toString()).slotId("S1").status(Booking.BookingStatus.COMPLETED).build());
            TrainingSession session=sessions.save(TrainingSession.builder().booking(booking).user(user).device(device).mode(TrainingSession.SessionMode.valueOf(mode.name())).startedAt(time.minusMinutes(10)).endedAt(time).actualDurationMin(10).build());
            int n=mode==Score.ScoreMode.final_?24:60;
            scores.save(Score.builder().session(session).mode(mode).totalScore(total).shotCount(n).shotScores("[]")
                .groupScores("[]").groupSpec(mode==Score.ScoreMode.final_?"[10,10,4]":"[10,10,10,10,10,10]").recordedAt(time).recordedBy(admin).build());
        });
    }
    JsonNode board(String weapon,String event,String metric,String gender)throws Exception {
        return data(mvc.perform(get("/api/leaderboard").param("weapon",weapon).param("event",event).param("metric",metric).param("gender",gender)).andExpect(status().isOk()));
    }
    @Test void rankingBestSeparatesWeaponsEventsGenderAndPrivateFields()throws Exception {
        historicalScore(a,pistol,Score.ScoreMode.final_,"2026-09-20T12:00:00",220);
        historicalScore(b,pistol,Score.ScoreMode.final_,"2026-09-19T12:00:00",220);
        historicalScore(a,rifle,Score.ScoreMode.final_,"2026-09-18T12:00:00",230);
        historicalScore(a,pistol,Score.ScoreMode.qualifying,"2026-09-17T12:00:00",580);
        JsonNode result=board("PISTOL","FINAL","BEST","ALL");
        assertEquals(2,result.path("rows").size());assertEquals("私密姓名b",result.at("/rows/0/displayName").asText());
        assertEquals(230,board("RIFLE","FINAL","BEST","ALL").at("/rows/0/score").asInt());
        assertEquals(580,board("PISTOL","QUALIFICATION","BEST","ALL").at("/rows/0/score").asInt());
        assertEquals("私密姓名a",board("PISTOL","FINAL","BEST","M").at("/rows/0/displayName").asText());
        for(String privateField:List.of("studentNo","phone","openid","PRIVATE-","昵称"))assertFalse(result.toString().contains(privateField));
    }
    @Test void rankingDoesNotInventNicknamesWhenRegisteredNameIsMissing() throws Exception {
        historicalScore(a,pistol,Score.ScoreMode.final_,"2026-09-20T12:00:00",220);
        jdbc.update("update profiles set real_name='' where user_id=?",a.getId());
        assertEquals(0,board("PISTOL","FINAL","BEST","ALL").path("rows").size());
    }
    @Test void rankingAverageUsesLatestFiveAndActualSampleSize()throws Exception {
        for(int i=0;i<6;i++)historicalScore(a,pistol,Score.ScoreMode.final_,"2026-09-"+(10+i)+"T12:00:00",i==0?240:100+i*10);
        historicalScore(b,pistol,Score.ScoreMode.final_,"2026-09-20T12:00:00",200);
        historicalScore(b,pistol,Score.ScoreMode.final_,"2026-09-21T12:00:00",220);
        JsonNode rows=board("PISTOL","FINAL","AVG5","ALL").path("rows");
        assertEquals(210,rows.get(0).path("score").asInt());assertEquals(2,rows.get(0).path("sampleCount").asInt());
        assertEquals(130,rows.get(1).path("score").asInt());assertEquals(5,rows.get(1).path("sampleCount").asInt());
    }
    @Test void rankingImprovementNeedsBothWindowsAndRejectsInvalidQuery()throws Exception {
        historicalScore(a,pistol,Score.ScoreMode.final_,"2026-08-10T12:00:00",180);
        historicalScore(a,pistol,Score.ScoreMode.final_,"2026-09-10T12:00:00",210);
        historicalScore(b,pistol,Score.ScoreMode.final_,"2026-09-12T12:00:00",230);
        JsonNode rows=board("PISTOL","FINAL","IMPROVE","ALL").path("rows");assertEquals(1,rows.size());assertEquals(30,rows.get(0).path("delta").asInt());
        assertEquals(230,board("PISTOL","FINAL","PB30D","ALL").at("/rows/0/score").asInt());
        mvc.perform(get("/api/leaderboard").param("weapon","PISTOL").param("event","FINAL").param("limit","1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/leaderboard").param("weapon","PISTOL").param("event","FINAL").param("metric","UNKNOWN")).andExpect(status().isBadRequest());
    }
}
