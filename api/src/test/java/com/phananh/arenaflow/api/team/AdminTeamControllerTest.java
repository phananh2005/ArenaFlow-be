package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminTeamControllerTest extends ApiIntegrationTest {

    @Test
    void nonAdminGets403Auth004() throws Exception {
        User user = saveUser("Plain User");
        flush();

        mockMvc.perform(as(user, get("/api/v1/admin/teams")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void markInactiveReactivateAndListWithReason() throws Exception {
        User admin = saveUser("Admin", User.Role.ADMIN);
        User captain = saveUser("Captain Adm");
        Team team = saveTeam("Team Admin", captain);
        flush();
        String base = "/api/v1/admin/teams/" + team.getId();

        mockMvc.perform(as(admin, patch(base + "/mark-inactive")).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(admin, patch(base + "/mark-inactive")).content("{\"reason\":\"Vi phạm\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.inactiveReason").value("Vi phạm"));
        flush();
        mockMvc.perform(as(admin, patch(base + "/mark-inactive")).content("{\"reason\":\"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_011"));

        mockMvc.perform(as(admin, get("/api/v1/admin/teams")).param("status", "INACTIVE").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.teamId == '" + team.getId() + "')].inactiveReason").value("Vi phạm"));

        mockMvc.perform(as(admin, patch(base + "/reactivate")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.inactiveReason").doesNotExist());
        flush();
        mockMvc.perform(as(admin, patch(base + "/reactivate")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_012"));
    }
}
