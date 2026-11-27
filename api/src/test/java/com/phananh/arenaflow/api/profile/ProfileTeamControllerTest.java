package com.phananh.arenaflow.api.profile;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.testsupport.UserDiscoveryFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Lời mời của tôi, đội của tôi (đầu-cuối trên DB dev). */
class ProfileTeamControllerTest extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserDiscoveryFixtures fixtures;

    @BeforeEach
    void setUpFixtures() {
        fixtures = new UserDiscoveryFixtures(jdbcTemplate);
    }

    @Test
    void myInvitationsShowsOnlyOwnPendingNewestFirstWithCurrentCaptain() throws Exception {
        User me = saveUser("Invitee");
        User other = saveUser("Other Invitee");
        flush();
        UUID lan = fixtures.user("Lan");
        UUID hung = fixtures.user("Hùng");
        UUID alpha = fixtures.team("Team Alpha", lan, "2026-10-01 10:00:00");
        fixtures.activeMember(alpha, hung, "2026-10-02 10:00:00");
        UUID alphaInvite = fixtures.invite(alpha, me.getId(), "2026-11-10 08:00:00");
        fixtures.transferCaptain(alpha, lan, hung);
        UUID beta = fixtures.team("Team Beta", fixtures.user("Beta Captain"));
        UUID betaInvite = fixtures.invite(beta, me.getId(), "2026-11-17 19:30:00");
        fixtures.deactivateTeam(beta);
        UUID gamma = fixtures.team("Team Gamma", fixtures.user("Gamma Captain"));
        fixtures.invite(gamma, me.getId(), "2026-11-12 08:00:00");
        fixtures.disbandTeam(gamma, lan);
        fixtures.invite(alpha, other.getId(), "2026-11-18 08:00:00");

        mockMvc.perform(as(me, get("/api/v1/profile/invitations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(2))
                .andExpect(jsonPath("$.data[0].invitationId").value(betaInvite.toString()))
                .andExpect(jsonPath("$.data[0].invitedAt").value("2026-11-17T19:30:00"))
                .andExpect(jsonPath("$.data[0].team.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data[1].invitationId").value(alphaInvite.toString()))
                .andExpect(jsonPath("$.data[1].team.captainName").value("Hùng"))
                .andExpect(jsonPath("$.data[1].team.memberCount").value(2))
                .andExpect(jsonPath("$.data[1].team.skillLevels[0]").value("Thi đấu"));
    }

    @Test
    void myInvitationsMasksDeletedCaptainAndRejectsDeletedCaller() throws Exception {
        User me = saveUser("Invitee");
        User gone = saveUser("Gone Caller");
        flush();
        UUID captain = fixtures.user("Captain To Delete");
        UUID team = fixtures.team("Team Delta", captain);
        fixtures.invite(team, me.getId(), "2026-11-10 08:00:00");
        fixtures.softDelete(captain);
        fixtures.softDelete(gone.getId());

        mockMvc.perform(as(me, get("/api/v1/profile/invitations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].team.captainName").value("Người dùng đã xóa"));
        mockMvc.perform(as(gone, get("/api/v1/profile/invitations")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_001"));
        mockMvc.perform(as(gone, get("/api/v1/profile/teams")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_001"));
    }

    @Test
    void myTeamsListsCaptainFirstThenNewestJoinedAndFiltersCaptain() throws Exception {
        User me = saveUser("Member Me");
        flush();
        UUID r = fixtures.team("Team R", me.getId(), "2026-10-01 10:00:00");
        UUID q = fixtures.team("Team Q", fixtures.user("Q Captain"), "2026-09-01 10:00:00");
        fixtures.activeMember(q, me.getId(), "2026-11-10 19:00:00");
        UUID p = fixtures.team("Team P", fixtures.user("P Captain"), "2026-09-01 10:00:00");
        fixtures.activeMember(p, me.getId(), "2026-11-01 19:00:00");
        fixtures.deactivateTeam(p);

        mockMvc.perform(as(me, get("/api/v1/profile/teams")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(3))
                .andExpect(jsonPath("$.data[0].teamId").value(r.toString()))
                .andExpect(jsonPath("$.data[0].role").value("CAPTAIN"))
                .andExpect(jsonPath("$.data[0].joinedAt").value("2026-10-01T10:00:00"))
                .andExpect(jsonPath("$.data[1].teamId").value(q.toString()))
                .andExpect(jsonPath("$.data[2].teamId").value(p.toString()))
                .andExpect(jsonPath("$.data[2].status").value("INACTIVE"));

        mockMvc.perform(as(me, get("/api/v1/profile/teams").param("role", "CAPTAIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(1))
                .andExpect(jsonPath("$.data[0].teamId").value(r.toString()));
    }

    @Test
    void emptyCaptainFilterMatchesAccountDeletionNoLongerBlocked() throws Exception {
        User me = saveUser("Captain Me");
        flush();
        UUID team = fixtures.team("Team Blocking", me.getId());

        mockMvc.perform(as(me, get("/api/v1/profile/teams").param("role", "CAPTAIN")))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
        mockMvc.perform(as(me, delete("/api/v1/profile")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("USER_003"));

        fixtures.disbandTeam(team, me.getId());

        mockMvc.perform(as(me, get("/api/v1/profile/teams").param("role", "CAPTAIN")))
                .andExpect(jsonPath("$.pagination.totalItems").value(0))
                .andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(as(me, delete("/api/v1/profile")))
                .andExpect(status().isNoContent());
    }

    @Test
    void eachUserSeesOnlyOwnDataIncludingAdmin() throws Exception {
        User admin = saveUser("Admin", User.Role.ADMIN);
        User member = saveUser("Someone");
        flush();
        fixtures.team("Someone Team", member.getId());

        mockMvc.perform(as(admin, get("/api/v1/profile/teams")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(0));
    }

    @Test
    void rejectsUnknownRoleAndInvalidPaging() throws Exception {
        User me = saveUser("Validator");
        flush();

        mockMvc.perform(as(me, get("/api/v1/profile/teams").param("role", "MEMBER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(me, get("/api/v1/profile/invitations").param("pageSize", "101")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(me, get("/api/v1/profile/teams").param("page", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/v1/profile/invitations"))
                .andExpect(status().isUnauthorized());
    }
}
