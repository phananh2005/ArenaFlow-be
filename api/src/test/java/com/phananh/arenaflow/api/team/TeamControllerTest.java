package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerTest extends ApiIntegrationTest {

    @Test
    void createTeamReturns201WithoutLocationOr400() throws Exception {
        User user = saveUser("Creator API");
        flush();

        mockMvc.perform(as(user, post("/api/v1/teams"))
                        .content("{\"teamName\":\"Team Alpha\",\"skillLevels\":[\"Chill\",\"Thi đấu\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
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
    void inviteReturns201WithoutLocationThen409OnDuplicate() throws Exception {
        User captain = saveUser("Captain I");
        User invitee = saveUser("Invitee I");
        Team team = saveTeam("Team Invite", captain);
        flush();
        String body = "{\"userId\":\"" + invitee.getId() + "\"}";

        mockMvc.perform(as(captain, post("/api/v1/teams/" + team.getId() + "/invitations")).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
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

    // ---------- Giải tán đội ----------

    @Test
    void disbandTeamGuardsThenDisbandsWithDefaultReasonAndRejectsRepeatAndWrites() throws Exception {
        User captain = saveUser("Disband Captain");
        User member = saveUser("Disband Member");
        Team team = saveTeam("Team Disband", captain);
        saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        flush();
        String url = "/api/v1/teams/" + team.getId() + "/disband";

        mockMvc.perform(post(url))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
        mockMvc.perform(as(captain, post("/api/v1/teams/" + UUID.randomUUID() + "/disband")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_001"));
        mockMvc.perform(as(member, post(url)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));
        mockMvc.perform(as(captain, post(url)).content("{\"reason\":\"" + "x".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // Không body, không Content-Type → lý do mặc định.
        mockMvc.perform(post(url).header(HttpHeaders.AUTHORIZATION, bearer(captain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBANDED"))
                .andExpect(jsonPath("$.data.disbandReason").value("Đội trưởng giải tán đội"))
                .andExpect(jsonPath("$.data.disbandedById").value(captain.getId().toString()))
                .andExpect(jsonPath("$.data.disbandedByName").value("Disband Captain"))
                .andExpect(jsonPath("$.data.disbandedAt").exists())
                .andExpect(jsonPath("$.data.captainId").doesNotExist())
                .andExpect(jsonPath("$.data.memberCount").value(0));

        mockMvc.perform(as(captain, post(url)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(captain, patch("/api/v1/teams/" + team.getId())).content("{\"teamName\":\"New\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(member, post("/api/v1/teams/" + team.getId() + "/members/leave")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(captain))
                        .param("status", "DISBANDED").param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.teamId == '" + team.getId() + "')].disbandedByName")
                        .value("Disband Captain"));
    }

    @Test
    void disbandTeamAcceptsEmptyObjectAndTrimsReason() throws Exception {
        User captain = saveUser("Trim Captain");
        Team emptyBody = saveTeam("Team Empty Body", captain);
        Team withReason = saveTeam("Team With Reason", captain);
        flush();

        mockMvc.perform(as(captain, post("/api/v1/teams/" + emptyBody.getId() + "/disband")).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbandReason").value("Đội trưởng giải tán đội"));
        mockMvc.perform(as(captain, post("/api/v1/teams/" + withReason.getId() + "/disband"))
                        .content("{\"reason\":\"  Hết người chơi  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disbandReason").value("Hết người chơi"));
    }

    /**
     * Đội đã giải tán, kiểm qua HTTP trên một đội: chỉ captain giải tán được; sau đó mọi thao tác ghi → 422 TEAM_016;
     * chi tiết và lịch sử captain vẫn xem được.
     */
    @Test
    void disbandedTeamBlocksWritesButStaysReadableEndToEnd() throws Exception {
        User captain = saveUser("AC Captain");
        User member = saveUser("AC Member");
        User invitee = saveUser("AC Invitee");
        User outsider = saveUser("AC Outsider");
        User admin = saveUser("AC Admin", User.Role.ADMIN);
        Team team = saveTeam("Team AC", captain);
        TeamMember activeMember = saveMember(team, member, TeamMember.Role.MEMBER, TeamMember.Status.ACTIVE);
        TeamMember invitation = saveMember(team, invitee, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        flush();
        String teamUrl = "/api/v1/teams/" + team.getId();
        mockMvc.perform(as(captain, post(teamUrl + "/captain/transfer"))
                        .content("{\"newCaptainId\":\"" + member.getId() + "\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(member, post(teamUrl + "/captain/transfer"))
                        .content("{\"newCaptainId\":\"" + captain.getId() + "\"}"))
                .andExpect(status().isOk());

        // Thành viên thường và Admin (không phải captain) bị từ chối, đội không đổi.
        mockMvc.perform(as(admin, post(teamUrl + "/disband")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));
        mockMvc.perform(as(member, post(teamUrl + "/disband")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));

        // Captain giải tán → 200.
        mockMvc.perform(as(captain, post(teamUrl + "/disband"))).andExpect(status().isOk());
        flush();
        TeamMember leftMember = teamMemberRepository.findById(activeMember.getId()).orElseThrow();
        assertThat(leftMember.getStatus()).isEqualTo(TeamMember.Status.LEFT);
        assertThat(leftMember.getLeftAt()).isNotNull();
        assertThat(teamMemberRepository.findById(invitation.getId()).orElseThrow().getStatus())
                .isEqualTo(TeamMember.Status.REJECTED);

        // Mọi thao tác ghi → 422 TEAM_016.
        mockMvc.perform(as(captain, post(teamUrl + "/invitations")).content("{\"userId\":\"" + outsider.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(invitee, post("/api/v1/invitations/" + invitation.getId() + "/accept")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(invitee, post("/api/v1/invitations/" + invitation.getId() + "/reject")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
        mockMvc.perform(as(captain, post(teamUrl + "/captain/transfer"))
                        .content("{\"newCaptainId\":\"" + member.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));

        // Chi tiết + lịch sử đội trưởng xem được; danh sách mặc định (ACTIVE) không có đội.
        mockMvc.perform(get(teamUrl).header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISBANDED"))
                .andExpect(jsonPath("$.data.disbandedByName").value("AC Captain"));
        mockMvc.perform(get(teamUrl + "/captain/history").header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(2)); // 2 lần chuyển quyền (fixture không ghi history tạo đội)
        mockMvc.perform(get("/api/v1/teams").header(HttpHeaders.AUTHORIZATION, bearer(outsider)).param("pageSize", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.teamId == '" + team.getId() + "')]").isEmpty());
    }
}
