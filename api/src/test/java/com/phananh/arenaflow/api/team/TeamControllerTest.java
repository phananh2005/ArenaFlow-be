package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerTest extends ApiIntegrationTest {

    @Test
    void createTeamReturns201WithLocationOr400() throws Exception {
        User user = saveUser("Creator API");
        flush();

        mockMvc.perform(as(user, post("/api/v1/teams"))
                        .content("{\"teamName\":\"Team Alpha\",\"skillLevels\":[\"Chill\",\"Thi đấu\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/teams/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.data.teamName").value("Team Alpha"))
                .andExpect(jsonPath("$.data.captainName").value("Creator API"))
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.skillLevels[1]").value("Thi đấu"));

        mockMvc.perform(as(user, post("/api/v1/teams"))
                        .content("{\"teamName\":\"X\",\"skillLevels\":[\"Chill\",\"Thi đấu\",\"Lão làng\",\"Vui chơi\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void getTeamAndPathValidation() throws Exception {
        User user = saveUser("Viewer API");
        Team team = saveTeam("Team Get", user);
        flush();

        mockMvc.perform(as(user, get("/api/v1/teams/" + team.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(team.getId().toString()));
        mockMvc.perform(as(user, get("/api/v1/teams/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_001"));
        mockMvc.perform(as(user, get("/api/v1/teams/not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listTeamsPaginationAndQueryValidation() throws Exception {
        User user = saveUser("Lister API");
        saveTeam("Team List", user);
        flush();

        mockMvc.perform(as(user, get("/api/v1/teams")).param("status", "ALL").param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.pageSize").value(1));
        mockMvc.perform(as(user, get("/api/v1/teams")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.pageSize").value(20));

        mockMvc.perform(as(user, get("/api/v1/teams")).param("status", "BOGUS"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(user, get("/api/v1/teams")).param("page", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("page"));
        mockMvc.perform(as(user, get("/api/v1/teams")).param("pageSize", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("pageSize"));
    }

    @Test
    void updateTeamOnlyByCaptain() throws Exception {
        User captain = saveUser("Captain U");
        User outsider = saveUser("Outsider U");
        Team team = saveTeam("Team Update", captain);
        flush();

        mockMvc.perform(as(outsider, patch("/api/v1/teams/" + team.getId())).content("{\"teamName\":\"Hacked\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));
        mockMvc.perform(as(captain, patch("/api/v1/teams/" + team.getId())).content("{\"teamName\":\"Renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamName").value("Renamed"));
    }

    @Test
    void inviteReturns201WithLocationThen409OnDuplicate() throws Exception {
        User captain = saveUser("Captain I");
        User invitee = saveUser("Invitee I");
        Team team = saveTeam("Team Invite", captain);
        flush();
        String body = "{\"userId\":\"" + invitee.getId() + "\"}";

        mockMvc.perform(as(captain, post("/api/v1/teams/" + team.getId() + "/invitations")).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, matchesPattern("/api/v1/invitations/[0-9a-f-]{36}")))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.teamName").value("Team Invite"));
        mockMvc.perform(as(captain, post("/api/v1/teams/" + team.getId() + "/invitations")).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_003"));
    }

    @Test
    void transferCaptainHistoryMembersAndLeave() throws Exception {
        User captain = saveUser("Captain T");
        User member = saveUser("Member T");
        Team team = saveTeam("Team Transfer", captain);
        saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flush();
        String base = "/api/v1/teams/" + team.getId();

        mockMvc.perform(as(captain, post(base + "/captain/transfer")).content("{\"newCaptainId\":\"" + captain.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_008"));
        mockMvc.perform(as(captain, post(base + "/captain/transfer"))
                        .content("{\"newCaptainId\":\"" + member.getId() + "\",\"reason\":\"Bận\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fromUserId").value(captain.getId().toString()))
                .andExpect(jsonPath("$.data.toUserId").value(member.getId().toString()))
                .andExpect(jsonPath("$.data.reason").value("Bận"));
        flush();

        mockMvc.perform(as(captain, get(base + "/captain/history")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].toUserId").value(member.getId().toString()))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
        mockMvc.perform(as(captain, get(base + "/members")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].userId").value(member.getId().toString()))
                .andExpect(jsonPath("$.data[0].role").value("CAPTAIN"))
                .andExpect(jsonPath("$.pagination.totalItems").value(2));

        mockMvc.perform(as(member, post(base + "/members/leave")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_009"));
        mockMvc.perform(as(captain, post(base + "/members/leave"))).andExpect(status().isNoContent());
    }
}
