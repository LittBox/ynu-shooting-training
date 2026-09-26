package com.ynu.shoting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.name=booking-flow-test",
        "spring.datasource.url=${test.database.url:jdbc:h2:mem:booking-flow-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL}",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class BookingFlowIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void newStudentCanRegisterBookAndCancelWithoutNullDefaultsOrEntityCycles() throws Exception {
        String login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"mode\":\"mock\",\"code\":\"booking-flow-regression\",\"nickname\":\"测试学员\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + mapper.readTree(login).path("data").path("token").asText();
        mvc.perform(post("/api/auth/profile").header("Authorization", token).contentType("application/json")
                        .content("{\"realName\":\"测试学员\",\"studentNo\":\"BOOKING-REGRESSION\",\"phone\":\"13800000000\",\"gender\":\"M\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.profileStatus").value("completed"))
                .andExpect(jsonPath("$.data.profile").doesNotExist());
        mvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.openid").doesNotExist());

        String date = LocalDate.now().plusDays(1).toString();
        String slots = mvc.perform(get("/api/bookings/slots").param("date", date))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode available = mapper.readTree(slots).path("data").get(0);
        assertNotNull(available);
        assertEquals(1, available.path("available").asInt());
        String payload = mapper.writeValueAsString(java.util.Map.of("deviceId", available.path("deviceId").asLong(), "slotDate", date, "slotId", "S1", "availabilityConfirmed", true));
        String result = mvc.perform(post("/api/bookings").header("Authorization", token)
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("BOOKED"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(result).path("data").path("id").asLong();
        mvc.perform(post("/api/bookings").header("Authorization", token)
                        .contentType("application/json").content(payload))
                .andExpect(status().is4xxClientError());
        mvc.perform(post("/api/bookings/" + id + "/cancel").header("Authorization", token))
                .andExpect(status().isOk());
        mvc.perform(get("/api/bookings/me").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("CANCELLED"));
    }
}
