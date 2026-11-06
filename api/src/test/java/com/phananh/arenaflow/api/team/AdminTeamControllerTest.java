package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    // ---------- Giải tán đội, lịch sử thành viên ----------

    @Test
    void memberHistoryIsAdminOnlyValidatesStatusAndListsDisbandedTeamMembers() throws Exception {
        User admin = saveUser("History Admin", User.Role.ADMIN);
        User captain = saveUser("History Captain");
        User member = saveUser("History Member");
        Team team = saveTeam("Team History", captain);
        saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flush();
        String url = "/api/v1/admin/teams/" + team.getId() + "/members";

        mockMvc.perform(as(captain, get(url)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(as(admin, get("/api/v1/admin/teams/" + UUID.randomUUID() + "/members")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_001"));
        mockMvc.perform(as(admin, get(url)).param("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(admin, get(url)).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].leftAt").doesNotExist());

        mockMvc.perform(as(captain, post("/api/v1/teams/" + team.getId() + "/disband"))).andExpect(status().isOk());

        mockMvc.perform(as(admin, get(url)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("LEFT"))))
                .andExpect(jsonPath("$.data[*].leftAt", everyItem(notNullValue())))
                .andExpect(jsonPath("$.data[?(@.userId == '" + captain.getId() + "')].role").value("CAPTAIN"))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));
        mockMvc.perform(as(admin, get(url)).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void markInactiveAndReactivateRejectDisbandedTeamWith422Team016() throws Exception {
        User admin = saveUser("Lock Admin", User.Role.ADMIN);
        User captain = saveUser("Lock Captain");
        Team team = saveTeam("Team Lock Disbanded", captain);
        flush();
        mockMvc.perform(as(captain, post("/api/v1/teams/" + team.getId() + "/disband"))).andExpect(status().isOk());
        String base = "/api/v1/admin/teams/" + team.getId();

        mockMvc.perform(as(admin, patch(base + "/mark-inactive")).content("{\"reason\":\"Vi phạm\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(admin, patch(base + "/reactivate")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(admin, get("/api/v1/admin/teams")).param("status", "DISBANDED").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.teamId == '" + team.getId() + "')].disbandedByName")
                        .value("Lock Captain"));
    }
}
