package com.ynu.shoting;

import com.fasterxml.jackson.databind.*;
import com.ynu.shoting.config.WechatReminderProperties;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.entity.WechatReminder.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.scheduler.WechatReminderScheduler;
import com.ynu.shoting.security.JwtTokenProvider;
import com.ynu.shoting.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.config.name=booking-flow-test","spring.datasource.url=${test.database.url:jdbc:h2:mem:reminder-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}","wechat-reminders.enabled=false"})
@AutoConfigureMockMvc @Import(BusinessIntegrationTest.TimeFixture.class) @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class WechatReminderIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired BusinessIntegrationTest.MutableClock clock;
    @Autowired UserRepository users; @Autowired DeviceRepository devices; @Autowired BookingRepository bookings;
    @Autowired AdminScheduleRepository schedules; @Autowired WechatReminderRepository reminders;
    @Autowired WechatReminderService service; @Autowired WechatReminderProperties config; @Autowired JwtTokenProvider tokens;
    @MockBean WechatSubscriptionClient client;
    User student,coach,other;Booking booking;AdminSchedule shift;String ts,tc,to;
    static final String TEMPLATE="tG676ieJiCyCYOqkxXA2wS2IkZX_ruvoK1atgECpLmE";
    @BeforeEach void setup() {
        for(String table:List.of("wechat_reminders","score_attempts","scores","training_sessions","cancellation_log","no_show_records","bookings","admin_schedules","user_availability","audit_log","profiles","users","devices"))jdbc.update("delete from "+table);
        clock.set("2026-09-26T08:00:00+08:00");
        config.setEnabled(true);config.setLeadMinutes(15);config.setLocation("楠院二栋C307");config.setMiniprogramState("trial");
        for(var template:List.of(config.getTraining(),config.getDuty())) {template.setTemplateId(TEMPLATE);template.setFieldsJson("{\"thing4\":\"title\",\"date3\":\"start\",\"time16\":\"end\",\"thing25\":\"location\"}");}
        student=users.save(User.builder().openid("wechat-student").role(User.Role.student).build());
        other=users.save(User.builder().openid("wechat-other").role(User.Role.student).build());
        coach=users.save(User.builder().openid("wechat-coach").role(User.Role.admin).build());
        var device=devices.save(Device.builder().name("气手枪1号").type(Device.DeviceType.pistol).build());
        booking=bookings.save(Booking.builder().user(student).device(device).slotDate("2026-09-26").slotId("S1").build());
        shift=schedules.save(AdminSchedule.builder().admin(coach).slotDate("2026-09-26").slotId("S1").build());
        ts="Bearer "+tokens.generateToken(student);tc="Bearer "+tokens.generateToken(coach);to="Bearer "+tokens.generateToken(other);
        when(client.configured()).thenReturn(true);when(client.send(anyMap())).thenReturn(new WechatSubscriptionClient.Result(WechatSubscriptionClient.Outcome.SENT,"0"));
    }
    ResultActions subscribe(String token,String kind,long id)throws Exception {
        return mvc.perform(post("/api/reminders/"+kind+"/"+id).header("Authorization",token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("templateId",TEMPLATE,"accepted",true))));
    }
    WechatReminder training() {return reminders.findByKindAndTargetId(Kind.TRAINING,booking.getId()).orElseThrow();}
    void at(String time) {clock.set("2026-09-26T"+time+":00+08:00");}
    void sweep() {new WechatReminderScheduler(service,client).sendDue();}
    @Test void trainingSendsOnlyOnceAtFifteenMinutesWithExactUserTemplate()throws Exception {
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk()).andExpect(jsonPath("$.data.scheduledAt").value("2026-09-26T08:15:00"));
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());assertEquals(1,reminders.count());
        at("08:14");sweep();verify(client,never()).send(anyMap());
        at("08:15");sweep();assertEquals(Status.SENT,training().getStatus());
        var payload=org.mockito.ArgumentCaptor.forClass(Map.class);verify(client).send(payload.capture());
        var body=json.valueToTree(payload.getValue());assertEquals("wechat-student",body.path("touser").asText());
        assertEquals(TEMPLATE,body.path("template_id").asText());assertEquals("手枪训练",body.at("/data/thing4/value").asText());
        assertEquals("2026-09-26 08:30",body.at("/data/date3/value").asText());assertEquals("2026-09-26 10:10",body.at("/data/time16/value").asText());
        assertEquals("楠院二栋C307",body.at("/data/thing25/value").asText());assertEquals("trial",body.path("miniprogram_state").asText());
        assertEquals("pages/booking/booking?view=mine",body.path("page").asText());
        sweep();at("08:16");sweep();verify(client,times(1)).send(anyMap());
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isConflict());
    }
    @Test void dutyRemindsTheScheduledCoachWithOwnTimeAndDestination()throws Exception {
        subscribe(tc,"DUTY",shift.getId()).andExpect(status().isOk());at("08:15");sweep();
        var payload=org.mockito.ArgumentCaptor.forClass(Map.class);verify(client).send(payload.capture());
        var body=json.valueToTree(payload.getValue());assertEquals("wechat-coach",body.path("touser").asText());
        assertEquals("射击场值班",body.at("/data/thing4/value").asText());assertEquals("pages/my/my?dutyDate=2026-09-26",body.path("page").asText());
    }
    @Test void rejectsMissingAuthOtherUserForgedTemplateAndRefusal()throws Exception {
        mvc.perform(get("/api/reminders/TRAINING/"+booking.getId())).andExpect(status().isUnauthorized());
        subscribe(to,"TRAINING",booking.getId()).andExpect(status().isForbidden());
        subscribe(tc,"TRAINING",booking.getId()).andExpect(status().isForbidden());
        subscribe(ts,"DUTY",shift.getId()).andExpect(status().isForbidden());
        mvc.perform(get("/api/reminders/TRAINING/"+booking.getId()).header("Authorization",to)).andExpect(status().isForbidden());
        for(String body:List.of("{\"templateId\":\"fake\",\"accepted\":true}","{\"templateId\":\""+TEMPLATE+"\",\"accepted\":false}"))
            mvc.perform(post("/api/reminders/TRAINING/"+booking.getId()).header("Authorization",ts).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        assertEquals(0,reminders.count());
    }
    @Test void bookingCancellationCheckinAndTrainingSuppressPendingMessages()throws Exception {
        for(var status:List.of(Booking.BookingStatus.CANCELLED,Booking.BookingStatus.CHECKED_IN,Booking.BookingStatus.IN_USE,Booking.BookingStatus.COMPLETED,Booking.BookingStatus.NO_SHOW)) {
            reminders.deleteAll();jdbc.update("update bookings set status='BOOKED' where id=?",booking.getId());at("08:00");
            subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());jdbc.update("update bookings set status=? where id=?",status.name(),booking.getId());
            at("08:15");sweep();assertEquals(Status.SKIPPED,training().getStatus());
        }
        verify(client,never()).send(anyMap());
    }
    @Test void coachArrivalDeletedShiftAndRoleRevocationSuppressReminders()throws Exception {
        subscribe(tc,"DUTY",shift.getId()).andExpect(status().isOk());jdbc.update("update admin_schedules set arrived_at=? where id=?",java.time.LocalDateTime.of(2026,9,26,8,10),shift.getId());at("08:15");sweep();
        assertEquals(Status.SKIPPED,reminders.findByKindAndTargetId(Kind.DUTY,shift.getId()).orElseThrow().getStatus());
        reminders.deleteAll();jdbc.update("update admin_schedules set arrived_at=null where id=?",shift.getId());at("08:00");subscribe(tc,"DUTY",shift.getId()).andExpect(status().isOk());
        users.findById(coach.getId()).ifPresent(u->{u.setRole(User.Role.student);users.save(u);});at("08:15");sweep();
        assertEquals(Status.SKIPPED,reminders.findByKindAndTargetId(Kind.DUTY,shift.getId()).orElseThrow().getStatus());
        reminders.deleteAll();coach.setRole(User.Role.admin);users.save(coach);at("08:00");subscribe(tc,"DUTY",shift.getId()).andExpect(status().isOk());
        schedules.deleteById(shift.getId());at("08:15");sweep();verify(client,never()).send(anyMap());
    }
    @Test void lateSubscriptionCatchesUpBeforeStartButNeverAfterStart()throws Exception {
        at("08:29");subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());sweep();assertEquals(Status.SENT,training().getStatus());
        reminders.deleteAll();at("08:00");subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());at("08:30");sweep();assertEquals(Status.SKIPPED,training().getStatus());
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isConflict());verify(client,times(1)).send(anyMap());
    }
    @Test void userCanCancelAndResubscribeWithoutCancellingTheBooking()throws Exception {
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());
        mvc.perform(delete("/api/reminders/TRAINING/"+booking.getId()).header("Authorization",ts)).andExpect(status().isOk());
        at("08:15");sweep();verify(client,never()).send(anyMap());assertEquals(Booking.BookingStatus.BOOKED,bookings.findById(booking.getId()).orElseThrow().getStatus());
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());sweep();verify(client).send(anyMap());assertEquals(1,reminders.count());
    }
    @Test void explicitTemporaryFailureRetriesWithinWindowAndPermissionFailureStops()throws Exception {
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());at("08:15");
        when(client.send(anyMap())).thenReturn(new WechatSubscriptionClient.Result(WechatSubscriptionClient.Outcome.RETRY,"-1"));
        sweep();sweep();verify(client,times(1)).send(anyMap());assertEquals(Status.PENDING,training().getStatus());
        at("08:16");sweep();at("08:17");sweep();assertEquals(Status.FAILED,training().getStatus());assertEquals(3,training().getAttempts());
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());
        when(client.send(anyMap())).thenReturn(new WechatSubscriptionClient.Result(WechatSubscriptionClient.Outcome.FAILED,"43101"));
        sweep();assertEquals(Status.FAILED,training().getStatus());assertEquals("43101",training().getLastCode());
        mvc.perform(get("/api/reminders/TRAINING/"+booking.getId()).header("Authorization",ts)).andExpect(jsonPath("$.data.canSubscribe").value(true));
    }
    @Test void uncertainDeliveryAndInterruptedProcessAreNotAutomaticallyRepeated()throws Exception {
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());at("08:15");
        when(client.send(anyMap())).thenReturn(new WechatSubscriptionClient.Result(WechatSubscriptionClient.Outcome.UNKNOWN,"DELIVERY_UNCERTAIN"));
        sweep();sweep();assertEquals(Status.UNKNOWN,training().getStatus());verify(client,times(1)).send(anyMap());
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isConflict());
        reminders.deleteAll();subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());
        assertNotNull(service.claim(training().getId()));at("08:18");sweep();assertEquals(Status.UNKNOWN,training().getStatus());verify(client,times(1)).send(anyMap());
    }
    @Test void twoWorkersCannotClaimTheSameReminder()throws Exception {
        subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());at("08:15");long id=training().getId();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var gate=new CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return service.claim(id);});var b=pool.submit(()->{gate.await();return service.claim(id);});gate.countDown();
            assertEquals(1,(a.get(10,TimeUnit.SECONDS)==null?0:1)+(b.get(10,TimeUnit.SECONDS)==null?0:1));
        }
    }
    @Test void configurationChangeAndDisabledFeatureDoNotUseOldAuthorization()throws Exception {
        config.setEnabled(false);subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isConflict());assertTrue(service.due().isEmpty());
        config.setEnabled(true);subscribe(ts,"TRAINING",booking.getId()).andExpect(status().isOk());config.getTraining().setTemplateId("new-template");
        mvc.perform(get("/api/reminders/TRAINING/"+booking.getId()).header("Authorization",ts)).andExpect(status().isOk()).andExpect(jsonPath("$.data.canSubscribe").value(true));
        at("08:15");sweep();
        assertEquals(Status.FAILED,training().getStatus());assertEquals("TEMPLATE_CHANGED",training().getLastCode());verify(client,never()).send(anyMap());
    }
}
