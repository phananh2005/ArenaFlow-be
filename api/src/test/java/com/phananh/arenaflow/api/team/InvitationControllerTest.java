package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.team.TeamMember;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvitationControllerTest extends ApiIntegrationTest {

    @Test
    void acceptOnlyByInviteeAndOnlyOnce() throws Exception {
        User captain = saveUser("Captain Inv");
        User invitee = saveUser("Invitee Inv");
        Team team = saveTeam("Team Inv", captain);
        TeamMember invitation = saveMember(team, invitee, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        flush();
        String accept = "/api/v1/invitations/" + invitation.getId() + "/accept";

        mockMvc.perform(as(captain, post(accept)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_005"));
        mockMvc.perform(as(invitee, post(accept)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.displayName").value("Invitee Inv"))
                .andExpect(jsonPath("$.data.joinedAt").isNotEmpty());
        flush();
        mockMvc.perform(as(invitee, post(accept)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_006"));
    }

    @Test
    void rejectAndUnknownInvitation() throws Exception {
        User captain = saveUser("Captain Rej");
        User invitee = saveUser("Invitee Rej");
        Team team = saveTeam("Team Rej", captain);
        TeamMember invitation = saveMember(team, invitee, TeamMember.Role.MEMBER, TeamMember.Status.PENDING);
        flush();

        mockMvc.perform(as(invitee, post("/api/v1/invitations/" + invitation.getId() + "/reject")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.teamName").value("Team Rej"));
        mockMvc.perform(as(invitee, post("/api/v1/invitations/" + UUID.randomUUID() + "/reject")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_004"));
    }
}
