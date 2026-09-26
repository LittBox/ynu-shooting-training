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

@SpringBootTest(properties={"spring.config.name=booking-flow-test", "spring.datasource.url=${test.database.url:jdbc:h2:mem:venue-duty-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}"})
@AutoConfigureMockMvc
@Import(BusinessIntegrationTest.TimeFixture.class)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class VenueDutyIntegrationTest {
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

    @Test void oneClassroomShiftOpensBothDevicesAndMultipleCoachesAreAllowed()throws Exception {
        JsonNode slots=data(mvc.perform(get("/api/bookings/slots").param("date",date)).andExpect(status().isOk()));
        int open=0;
        for(JsonNode row:slots)if(row.path("id").asText().equals("S1")) {
            assertTrue(row.path("staffed").asBoolean());assertEquals(1,row.path("available").asInt());
            assertFalse(row.path("coachPresent").asBoolean());open++;
        }
        assertEquals(2,open);
        assertEquals(shift,data(add(coach,date,"S1").andExpect(status().isOk())).path("id").asLong());
        add(relief,date,"S1").andExpect(status().isOk());assertEquals(2,schedules.count());
        bookResponse(ts,pistol,"S1").andExpect(status().isOk());bookResponse(to,rifle,"S1").andExpect(status().isOk());
    }
    @Test void scheduledButAbsentCoachCannotStartTrainingAndAttendanceIsSelfOnly()throws Exception {
        long booking=checkedIn(ts,pistol,"S1");
        start(ts,booking).andExpect(status().isConflict());assertEquals(0,sessions.count());
        arrive(ts,shift).andExpect(status().isForbidden());arrive(tr,shift).andExpect(status().isForbidden());
        arrive(tc,shift).andExpect(status().isOk());arrive(tc,shift).andExpect(status().isOk());
        assertEquals(1,audits.findAll().stream().filter(a->a.getAction().equals("coach_arrive")).count());
        start(ts,booking).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/schedules/"+shift).header("Authorization",tc)).andExpect(status().isConflict());
    }
    @Test void lastCoachCannotLeaveUntilEveryDeviceFinishes()throws Exception {
        long a=checkedIn(ts,pistol,"S1"),b=checkedIn(to,rifle,"S1");arrive(tc,shift).andExpect(status().isOk());
        long sa=data(start(ts,a).andExpect(status().isOk())).path("id").asLong();
        long sb=data(start(to,b).andExpect(status().isOk())).path("id").asLong();
        leave(tc).andExpect(status().isConflict());finish(ts,sa);leave(tc).andExpect(status().isConflict());
        finish(to,sb);leave(tc).andExpect(status().isOk());
        assertNotNull(schedules.findById(shift).orElseThrow().getDepartedAt());
    }
    @Test void reliefMustActuallyArriveBeforeHandover()throws Exception {
        long booking=checkedIn(ts,pistol,"S1");arrive(tc,shift).andExpect(status().isOk());start(ts,booking).andExpect(status().isOk());
        long second=data(add(relief,date,"S1").andExpect(status().isOk())).path("id").asLong();
        leave(tc).andExpect(status().isConflict());arrive(tr,second).andExpect(status().isOk());
        leave(tc).andExpect(status().isOk());leave(tr).andExpect(status().isConflict());
        assertEquals(1,sessions.countByEndedAtIsNull());
    }
    @Test void onlyCurrentSlotAttendanceCanAuthorizeNewTrainingAndReentryIsAudited()throws Exception {
        arrive(tc,shift).andExpect(status().isOk());
        long second=data(add(coach,date,"S2").andExpect(status().isOk())).path("id").asLong();
        arrive(tc,second).andExpect(status().isConflict());
        clock.set("2026-09-25T10:00:00+08:00");
        long booking=checkedIn(ts,pistol,"S2");start(ts,booking).andExpect(status().isConflict());
        leave(tc).andExpect(status().isOk());arrive(tc,second).andExpect(status().isOk());
        leave(tc).andExpect(status().isOk());arrive(tc,second).andExpect(status().isOk());
        start(ts,booking).andExpect(status().isOk());
        assertEquals(3,audits.findAll().stream().filter(a->a.getAction().equals("coach_arrive")).count());
    }
    @Test void expiredShiftNeverSilentlyReleasesTheLastCoachAndOwnOtherShiftIsNotRelief()throws Exception {
        long booking=checkedIn(ts,pistol,"S1");arrive(tc,shift).andExpect(status().isOk());
        long session=data(start(ts,booking).andExpect(status().isOk())).path("id").asLong();
        long next=data(add(coach,date,"S2").andExpect(status().isOk())).path("id").asLong();
        clock.set("2026-09-25T10:00:00+08:00");arrive(tc,next).andExpect(status().isOk());
        leave(tc).andExpect(status().isConflict());
        clock.set("2026-09-25T10:30:00+08:00");leave(tc).andExpect(status().isConflict());
        finish(ts,session);leave(tc).andExpect(status().isOk());
        assertEquals(0,schedules.findByAdminIdAndArrivedAtIsNotNullAndDepartedAtIsNull(coach.getId()).size());
    }
    @Test void arrivalWindowRejectsWrongDayAndExpiredShifts()throws Exception {
        long tomorrow=data(add(coach,"2026-09-26","S1").andExpect(status().isOk())).path("id").asLong();
        arrive(tc,tomorrow).andExpect(status().isConflict());
        clock.set("2026-09-25T07:59:59+08:00");arrive(tc,shift).andExpect(status().isConflict());
        clock.set("2026-09-25T10:10:00+08:00");arrive(tc,shift).andExpect(status().isConflict());
    }
    @Test void cannotDeleteLastShiftWhenAnyClassroomDeviceIsReserved()throws Exception {
        bookResponse(ts,rifle,"S1").andExpect(status().isOk());
        mvc.perform(delete("/api/admin/schedules/"+shift).header("Authorization",tc)).andExpect(status().isConflict());
        long otherShift=data(add(relief,date,"S1").andExpect(status().isOk())).path("id").asLong();
        mvc.perform(delete("/api/admin/schedules/"+shift).header("Authorization",tc)).andExpect(status().isOk());
        mvc.perform(delete("/api/admin/schedules/"+otherShift).header("Authorization",tr)).andExpect(status().isConflict());
    }
    @Test void concurrentDeparturesKeepOneCoachOnSite()throws Exception {
        long booking=checkedIn(ts,pistol,"S1");arrive(tc,shift).andExpect(status().isOk());start(ts,booking).andExpect(status().isOk());
        long second=data(add(relief,date,"S1").andExpect(status().isOk())).path("id").asLong();arrive(tr,second).andExpect(status().isOk());
        assertEquals(List.of(200,409),race(()->code(leave(tc)),()->code(leave(tr))));
        assertEquals(1,schedules.findAll().stream().filter(s->s.getArrivedAt()!=null&&s.getDepartedAt()==null).count());
    }
    @Test void simultaneousStartAndDepartureCannotLeaveUnsupervisedTraining()throws Exception {
        long booking=checkedIn(ts,pistol,"S1");arrive(tc,shift).andExpect(status().isOk());
        assertEquals(List.of(200,409),race(()->code(start(ts,booking)),()->code(leave(tc))));
        boolean present=schedules.findById(shift).orElseThrow().getDepartedAt()==null;
        assertEquals(present?1:0,sessions.countByEndedAtIsNull());
    }
    @Test void simultaneousBookingAndLastScheduleDeletionCannotLeaveUnstaffedBooking()throws Exception {
        assertEquals(List.of(200,409),race(()->code(bookResponse(ts,rifle,"S1")),
                ()->code(mvc.perform(delete("/api/admin/schedules/"+shift).header("Authorization",tc)))));
        if(bookings.count()>0)assertEquals(1,schedules.count());
    }
    ResultActions walkIn(String token, Device device, String day, String slot, String mode)throws Exception {
        var req=post("/api/bookings/walk-in").contentType("application/json")
                .content(json.writeValueAsString(Map.of("deviceId",device.getId(),"slotDate",day,"slotId",slot,"mode",mode)));
        if(token!=null)req.header("Authorization",token);
        return mvc.perform(req);
    }
    ResultActions walkIn(String token,Device device)throws Exception { return walkIn(token,device,date,"S1","qualifying"); }
    JsonNode pistolSlot()throws Exception {
        return pistolSlot(date,"S1");
    }
    JsonNode pistolSlot(String day,String slot)throws Exception {
        for(JsonNode row:data(mvc.perform(get("/api/bookings/slots").param("date",day)).andExpect(status().isOk())))
            if(row.path("id").asText().equals(slot) && row.path("deviceId").asLong()==pistol.getId())return row;
        throw new AssertionError("slot missing");
    }
    @Test void busyDeviceAllowsUnoccupiedFutureSlotsButNotImmediateTraining()throws Exception {
        add(coach,date,"S2").andExpect(status().isOk());
        add(coach,"2026-09-26","S1").andExpect(status().isOk());
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        long activeSession=data(walkIn(ts,pistol).andExpect(status().isOk())).path("sessionId").asLong();
        assertFalse(pistolSlot().path("walkInAvailable").asBoolean());
        walkIn(to,pistol).andExpect(status().isConflict());
        for(String[] target:List.of(new String[]{date,"S2"},new String[]{"2026-09-26","S1"})) {
            JsonNode row=pistolSlot(target[0],target[1]);
            assertEquals("IN_USE",row.path("deviceStatus").asText());
            assertEquals(1,row.path("available").asInt());
            assertFalse(row.path("walkInAvailable").asBoolean());
            JsonNode detail=data(mvc.perform(get("/api/bookings/slots/details")
                    .param("date",target[0]).param("slotId",target[1])).andExpect(status().isOk()));
            boolean found=false;
            for(JsonNode device:detail.path("devices"))if(device.path("deviceId").asLong()==pistol.getId()) {
                found=true;
                assertTrue(device.path("available").asBoolean());
                assertFalse(device.path("walkInAvailable").asBoolean());
                assertEquals(0,device.path("reservations").size());
            }
            assertTrue(found);
        }
        // No coach schedule still closes a future slot, even with no reservation.
        assertEquals(0,pistolSlot(date,"S3").path("available").asInt());
        bookResponse(to,pistol,"S3").andExpect(status().isConflict());
        long next=data(bookResponse(to,pistol,"S2").andExpect(status().isOk())).path("id").asLong();
        mvc.perform(post("/api/bookings").header("Authorization",to).contentType("application/json")
                .content(json.writeValueAsString(Map.of("deviceId",pistol.getId(),"slotDate","2026-09-26","slotId","S1","availabilityConfirmed",true))))
                .andExpect(status().isOk());
        assertEquals(0,pistolSlot(date,"S2").path("available").asInt());
        bookResponse(tr,pistol,"S2").andExpect(status().isConflict());
        assertEquals(Device.DeviceStatus.IN_USE,devices.findById(pistol.getId()).orElseThrow().getStatus());
        assertNull(sessions.findById(activeSession).orElseThrow().getEndedAt());
        // A future reservation cannot start on top of the current training.
        clock.set("2026-09-25T10:00:00+08:00");
        mvc.perform(post("/api/bookings/"+next+"/checkin").header("Authorization",to)).andExpect(status().isOk());
        long nextShift=schedules.findBySlotDate(date).stream().filter(s->s.getSlotId().equals("S2")).findFirst().orElseThrow().getId();
        arrive(tc,nextShift).andExpect(status().isOk());
        start(to,next).andExpect(status().isConflict());
        finish(ts,activeSession);
        clock.set("2026-09-25T10:30:00+08:00");
        start(to,next).andExpect(status().isOk());
        assertEquals(1,sessions.countByEndedAtIsNull());
    }
    @Test void futureBookingsStillRejectMaintenanceAndDisabledDevices()throws Exception {
        for(String state:List.of("MAINTENANCE","DISABLED")) {
            jdbc.update("update devices set status=? where id=?",state,pistol.getId());
            assertEquals(0,pistolSlot().path("available").asInt());
            bookResponse(ts,pistol,"S1").andExpect(status().isConflict());
        }
        assertEquals(0,bookings.count());
    }
    @Test void concurrentFutureReservationsRemainExclusiveWhileDeviceIsBusy()throws Exception {
        add(coach,date,"S2").andExpect(status().isOk());
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        walkIn(ts,pistol).andExpect(status().isOk());
        assertEquals(List.of(200,409),race(()->code(bookResponse(to,pistol,"S2")),()->code(bookResponse(tr,pistol,"S2"))));
        assertEquals(1,bookings.findBySlotDateAndSlotId(date,"S2").size());
        assertEquals(1,sessions.countByEndedAtIsNull());
        assertEquals(Device.DeviceStatus.IN_USE,devices.findById(pistol.getId()).orElseThrow().getStatus());
    }
    @Test void walkInStartsImmediatelyAndCompletedSlotCanBeReusedWithHistoryIntact()throws Exception {
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        assertTrue(pistolSlot().path("walkInAvailable").asBoolean());
        assertEquals(0,pistolSlot().path("available").asInt());
        JsonNode first=data(walkIn(ts,pistol).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_USE")));
        assertEquals("qualifying",sessions.findById(first.path("sessionId").asLong()).orElseThrow().getMode().name());
        assertFalse(pistolSlot().path("walkInAvailable").asBoolean());
        walkIn(to,pistol).andExpect(status().isConflict());
        finish(ts,first.path("sessionId").asLong());
        assertNull(bookings.findById(first.path("id").asLong()).orElseThrow().getOccupiedDeviceSlot());
        // Reproduce a completed record written by the old deployment.
        jdbc.update("update bookings set occupied_device_slot=? where id=?",pistol.getId()+":"+date+":S1",first.path("id").asLong());
        assertTrue(pistolSlot().path("walkInAvailable").asBoolean());
        walkIn(to,pistol).andExpect(status().isOk());
        assertEquals(2,sessions.count());assertEquals(1,sessions.countByEndedAtIsNull());
        assertEquals(Booking.BookingStatus.COMPLETED,bookings.findById(first.path("id").asLong()).orElseThrow().getStatus());
    }
    @Test void walkInRequiresLoginCompleteProfileCurrentSlotAndPresentCoach()throws Exception {
        walkIn(null,pistol).andExpect(status().isUnauthorized());
        arrive(tc,shift).andExpect(status().isOk());
        walkIn(ts,pistol).andExpect(status().isConflict());
        clock.set("2026-09-25T08:30:00+08:00");
        walkIn(ts,pistol,date,"S2","qualifying").andExpect(status().isConflict());
        walkIn(ts,pistol,"2026-09-26","S1","qualifying").andExpect(status().isConflict());
        walkIn(ts,pistol,date,"S1","unknown").andExpect(status().isBadRequest());
        jdbc.update("update profiles set gender='U' where user_id=?",student.getId());
        walkIn(ts,pistol).andExpect(status().isBadRequest());
        jdbc.update("update profiles set gender='M' where user_id=?",student.getId());
        leave(tc).andExpect(status().isOk());walkIn(ts,pistol).andExpect(status().isConflict());
        assertFalse(pistolSlot().path("walkInAvailable").asBoolean());
        arrive(tc,shift).andExpect(status().isOk());
        clock.set("2026-09-25T10:10:00+08:00");walkIn(ts,pistol).andExpect(status().isConflict());
        assertEquals(0,bookings.count());assertEquals(0,sessions.count());
    }
    @Test void walkInPreservesCheckinDeadlineThenReleasesOverdueNoShowAtomically()throws Exception {
        long reserved=data(bookResponse(ts,pistol,"S1").andExpect(status().isOk())).path("id").asLong();
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T08:40:00+08:00");
        walkIn(to,pistol).andExpect(status().isConflict());assertFalse(pistolSlot().path("walkInAvailable").asBoolean());
        clock.set("2026-09-25T08:40:01+08:00");assertTrue(pistolSlot().path("walkInAvailable").asBoolean());
        walkIn(to,pistol).andExpect(status().isOk());
        assertEquals(Booking.BookingStatus.NO_SHOW,bookings.findById(reserved).orElseThrow().getStatus());
        assertEquals(1,jdbc.queryForObject("select count(*) from no_show_records",Integer.class));
    }
    @Test void checkedInReservationRemainsProtectedButCancellationAllowsWalkIn()throws Exception {
        long reserved=checkedIn(ts,pistol,"S1");arrive(tc,shift).andExpect(status().isOk());
        clock.set("2026-09-25T09:00:00+08:00");walkIn(to,pistol).andExpect(status().isConflict());
        mvc.perform(post("/api/bookings/"+reserved+"/cancel").header("Authorization",tc)).andExpect(status().isOk());
        walkIn(to,pistol).andExpect(status().isOk());
    }
    @Test void walkInCannotUseMaintenanceDisabledOrBusyDevice()throws Exception {
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        for(String state:List.of("MAINTENANCE","DISABLED","IN_USE")) {
            jdbc.update("update devices set status=? where id=?",state,pistol.getId());
            assertFalse(pistolSlot().path("walkInAvailable").asBoolean());
            walkIn(ts,pistol).andExpect(status().isConflict());
        }
        assertEquals(0,sessions.count());
    }
    @Test void concurrentWalkInsAndCoachDepartureRemainExclusive()throws Exception {
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        assertEquals(List.of(200,409),race(()->code(walkIn(ts,pistol)),()->code(walkIn(to,pistol))));
        assertEquals(1,sessions.countByEndedAtIsNull());leave(tc).andExpect(status().isConflict());
    }
    @Test void concurrentWalkInAndDepartureCannotLeaveUnsupervisedTraining()throws Exception {
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        assertEquals(List.of(200,409),race(()->code(walkIn(ts,pistol)),()->code(leave(tc))));
        boolean present=schedules.findById(shift).orElseThrow().getDepartedAt()==null;
        assertEquals(present?1:0,sessions.countByEndedAtIsNull());
    }
    @Test void walkInCountsTowardQuotaAndPreventsSameUserUsingTwoDevices()throws Exception {
        for(String slot:List.of("S2","S3","S4")) {
            add(coach,date,slot).andExpect(status().isOk());bookResponse(ts,pistol,slot).andExpect(status().isOk());
        }
        arrive(tc,shift).andExpect(status().isOk());clock.set("2026-09-25T09:00:00+08:00");
        walkIn(ts,pistol).andExpect(status().isTooManyRequests());
        JsonNode session=data(walkIn(to,pistol).andExpect(status().isOk()));
        walkIn(to,rifle).andExpect(status().isConflict());
        finish(to,session.path("sessionId").asLong());
        walkIn(to,rifle).andExpect(status().isConflict());
        assertEquals(1,sessions.count());
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
}
