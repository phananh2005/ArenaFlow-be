package com.phananh.arenaflow.api.sport;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.SportRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Bộ môn 2.13–2.15 qua HTTP thật (DB dev). */
class SportControllerTest extends ApiIntegrationTest {

    @Autowired
    private SportRepository sportRepository;

    @Test
    void listActiveSportsHidesDisabledSport() throws Exception {
        User user = saveUser("Người xem");
        Sport disabled = sportRepository.save(Sport.builder().name("Tắt " + UUID.randomUUID()).isActive(false).build());

        String body = mockMvc.perform(as(user, get("/api/v1/sports").param("pageSize", "100")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(disabled.getId().toString());
    }

    @Test
    void adminTogglesSportAndNonAdminIsRejected() throws Exception {
        User admin = saveUser("Admin", User.Role.ADMIN);
        User user = saveUser("Thường");
        Sport sport = sportRepository.save(Sport.builder().name("Bật " + UUID.randomUUID()).isActive(true).build());

        mockMvc.perform(as(user, patch("/api/v1/admin/sports/" + sport.getId())).content("{\"isActive\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));

        mockMvc.perform(as(admin, patch("/api/v1/admin/sports/" + sport.getId())).content("{\"isActive\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sportId").value(sport.getId().toString()))
                .andExpect(jsonPath("$.data.isActive").value(false));

        mockMvc.perform(as(admin, get("/api/v1/admin/sports").param("isActive", "false").param("pageSize", "100")))
                .andExpect(status().isOk());
    }

    @Test
    void missingSportIsSport001() throws Exception {
        User admin = saveUser("Admin", User.Role.ADMIN);

        mockMvc.perform(as(admin, patch("/api/v1/admin/sports/" + UUID.randomUUID())).content("{\"isActive\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("SPORT_001"));
    }
}
