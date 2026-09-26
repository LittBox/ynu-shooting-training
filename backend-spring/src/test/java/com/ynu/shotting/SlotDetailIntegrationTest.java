package com.ynu.shoting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ynu.shoting.entity.*;
import com.ynu.shoting.repository.*;
import com.ynu.shoting.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.config.name=booking-flow-test",
        "spring.datasource.url=${test.database.url:jdbc:h2:mem:slot-detail-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class SlotDetailIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired DeviceRepository devices;
    @Autowired BookingRepository bookings;
    @Autowired AuditLogRepository audits;
    @Autowired JwtTokenProvider tokens;

    @Test
    void rosterProjectsIdentityByRoleAndAuditsStaffAccess() throws Exception {
        User owner = User.builder().openid("roster-owner-private").nickname("专注同学").build();
        owner.setProfile(Profile.builder().user(owner).realName("真实姓名").studentNo("PRIVATE-2026")
                .phone("13812345678").build());
        owner = users.saveAndFlush(owner);
        User viewer = users.saveAndFlush(User.builder().openid("roster-viewer").nickname("其他同学").build());
        User coach = users.saveAndFlush(User.builder().openid("roster-coach").role(User.Role.admin).build());
        Device device = devices.saveAndFlush(Device.builder().name("详情测试步枪").type(Device.DeviceType.rifle).status(Device.DeviceStatus.IN_USE).build());
        String date = LocalDate.now().toString();
        Booking active = bookings.saveAndFlush(Booking.builder().user(owner).device(device).slotDate(date).slotId("S1").status(Booking.BookingStatus.IN_USE).build());
        bookings.saveAndFlush(Booking.builder().user(viewer).device(device).slotDate(date).slotId("S1").status(Booking.BookingStatus.CANCELLED).build());
        long auditCount = audits.count();

        JsonNode guest = read(date, null);
        assertEquals("GUEST", guest.path("viewerScope").asText());
        JsonNode reservation = row(guest, device.getId()).path("reservations").get(0);
        assertFalse(reservation.has("displayName"));
        assertFalse(reservation.has("studentNo"));
        assertEquals("IN_USE", reservation.path("status").asText());
        assertEquals(1, row(guest, device.getId()).path("reservations").size());

        JsonNode student = read(date, viewer);
        assertEquals("专注同学", row(student, device.getId()).path("reservations").get(0).path("displayName").asText());
        assertFalse(student.toString().contains("真实姓名"));
        assertFalse(student.toString().contains("PRIVATE-2026"));
        assertEquals(auditCount, audits.count());
        assertTrue(row(read(date, owner), device.getId()).path("reservations").get(0).path("mine").asBoolean());

        JsonNode staff = read(date, coach);
        reservation = row(staff, device.getId()).path("reservations").get(0);
        assertEquals("STAFF", staff.path("viewerScope").asText());
        assertEquals("真实姓名", reservation.path("displayName").asText());
        assertEquals("PRIVATE-2026", reservation.path("studentNo").asText());
        assertFalse(staff.toString().contains("13812345678"));
        assertFalse(staff.toString().contains("roster-owner-private"));
        assertEquals(auditCount + 1, audits.count());
        assertTrue(audits.findAll().stream().anyMatch(log -> "VIEW_SLOT_ROSTER".equals(log.getAction())));

        active.setStatus(Booking.BookingStatus.COMPLETED); bookings.saveAndFlush(active);
        assertEquals("COMPLETED", row(read(date, viewer), device.getId()).path("reservations").get(0).path("status").asText());
        // Role claims cannot retain staff visibility after a database demotion.
        String oldStaffToken = "Bearer " + tokens.generateToken(coach);
        coach.setRole(User.Role.student); users.saveAndFlush(coach);
        String downgraded = mvc.perform(get("/api/bookings/slots/details").param("date", date).param("slotId", "S1").header("Authorization", oldStaffToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(downgraded.contains("PRIVATE-2026"));
    }

    @Test
    void invalidTokenAndInvalidSlotFailClosed() throws Exception {
        mvc.perform(get("/api/bookings/slots/details").param("date", "2026-09-25").param("slotId", "S1")
                .header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings/slots/details").param("date", "2026-09-25").param("slotId", "S99"))
                .andExpect(status().is4xxClientError());
    }

    private JsonNode read(String date, User viewer) throws Exception {
        var request = get("/api/bookings/slots/details").param("date", date).param("slotId", "S1");
        if (viewer != null) request.header("Authorization", "Bearer " + tokens.generateToken(viewer));
        return mapper.readTree(mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("data");
    }
    private JsonNode row(JsonNode response, Long deviceId) {
        for (JsonNode row : response.path("devices")) if (row.path("deviceId").asLong() == deviceId) return row;
        throw new AssertionError("Missing device " + deviceId);
    }
}
