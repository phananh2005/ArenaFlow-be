package com.phananh.arenaflow.api.team;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.testsupport.UserDiscoveryFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tìm người để mời (đầu-cuối trên DB dev). Bộ đếm giới hạn là bean dùng chung trong context nên mỗi test dùng người
 * gọi mới.
 */
class TeamInvitationCandidatesControllerTest extends ApiIntegrationTest {

    private static final String PATH = "/api/v1/teams/{teamId}/invitation-candidates";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UserDiscoveryFixtures fixtures;
    private String word;
    private User captain;
    private UUID team;

    @BeforeEach
    void setUpTeam() {
        fixtures = new UserDiscoveryFixtures(jdbcTemplate);
        word = UserDiscoveryFixtures.uniqueWord();
        captain = saveUser("Captain " + word);
        flush();
        team = fixtures.team("Team " + word, captain.getId());
    }

    @Test
    void searchesByNameWithLabelsAndWithoutPhoneNumbers() throws Exception {
        String phone = UserDiscoveryFixtures.uniquePhone();
        UUID active = fixtures.user(word + " Active", phone, "USER");
        UUID pending = fixtures.user(word + " Pending");
        fixtures.user(word + " Free");
        fixtures.activeMember(team, active, UserDiscoveryFixtures.DEFAULT_TIME);
        fixtures.invite(team, pending, UserDiscoveryFixtures.DEFAULT_TIME);

        mockMvc.perform(as(captain, get(PATH, team).param("q", word.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(jsonPath("$.data.items.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].displayName").value(word + " Active"))
                .andExpect(jsonPath("$.data.items[0].membershipStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.items[1].displayName").value(word + " Free"))
                .andExpect(jsonPath("$.data.items[1].membershipStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.items[2].membershipStatus").value("PENDING"))
                .andExpect(content().string(not(containsString(phone))))
                .andExpect(content().string(not(containsString("phone"))));
    }

    @Test
    void searchesByNormalizedPhoneWithoutReturningIt() throws Exception {
        String phone = UserDiscoveryFixtures.uniquePhone();
        UUID target = fixtures.user("Phone Target " + word, phone, "USER");
        String international = "+84 " + phone.substring(1, 4) + " " + phone.substring(4, 7) + " " + phone.substring(7);

        mockMvc.perform(as(captain, get(PATH, team).param("q", international)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].userId").value(target.toString()))
                .andExpect(jsonPath("$.data.hasMore").value(false))
                .andExpect(content().string(not(containsString(phone))));
    }

    @Test
    void returnsTwentyItemsAndHasMoreWhenMoreMatch() throws Exception {
        for (int i = 1; i <= 25; i++) {
            fixtures.user(word + " Người " + String.format("%02d", i));
        }

        mockMvc.perform(as(captain, get(PATH, team).param("q", word)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(20))
                .andExpect(jsonPath("$.data.hasMore").value(true));
    }

    @Test
    void reportsInputAndTeamErrorsInCheckOrder() throws Exception {
        mockMvc.perform(as(captain, get(PATH, team).param("q", "+84 12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_005"));
        mockMvc.perform(as(captain, get(PATH, team).param("q", " a ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("USER_006"));
        mockMvc.perform(as(captain, get(PATH, UUID.randomUUID()).param("q", "an")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_001"));

        User member = saveUser("Member " + word);
        flush();
        fixtures.activeMember(team, member.getId(), UserDiscoveryFixtures.DEFAULT_TIME);
        mockMvc.perform(as(member, get(PATH, team).param("q", "an")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));

        fixtures.deactivateTeam(team);
        mockMvc.perform(as(captain, get(PATH, team).param("q", "an")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TEAM_015"));
    }

    @Test
    void rejectsMissingBlankOrTooLongQueryAsValidationError() throws Exception {
        mockMvc.perform(as(captain, get(PATH, team)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(captain, get(PATH, team).param("q", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(captain, get(PATH, team).param("q", "a".repeat(101))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(captain, get(PATH, "not-a-uuid").param("q", "an")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get(PATH, team).param("q", "an"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void thirtyFirstCallWithinAMinuteAcrossTeamsIsRejectedWith429() throws Exception {
        UUID otherTeam = fixtures.team("Other " + word, captain.getId());
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(as(captain, get(PATH, team).param("q", word))).andExpect(status().isOk());
        }
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(as(captain, get(PATH, otherTeam).param("q", word))).andExpect(status().isOk());
        }

        mockMvc.perform(as(captain, get(PATH, team).param("q", word)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.error.code").value("USER_007"))
                .andExpect(jsonPath("$.error.message").value("Bạn tìm quá nhanh, vui lòng thử lại sau"))
                .andExpect(jsonPath("$.error.path").exists());
    }

    @Test
    void failedSearchesCountButValidationErrorsDoNot() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(as(captain, get(PATH, team).param("q", "   "))).andExpect(status().isBadRequest());
        }
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(as(captain, get(PATH, team).param("q", "+84 12345")))
                    .andExpect(jsonPath("$.error.code").value("USER_005"));
        }
        for (int i = 0; i < 15; i++) {
            mockMvc.perform(as(captain, get(PATH, UUID.randomUUID()).param("q", word)))
                    .andExpect(jsonPath("$.error.code").value("TEAM_001"));
        }

        mockMvc.perform(as(captain, get(PATH, team).param("q", word)))
                .andExpect(status().isTooManyRequests());
        mockMvc.perform(as(captain, get("/api/v1/profile/teams"))).andExpect(status().isOk());
        mockMvc.perform(as(captain, get("/api/v1/profile/invitations"))).andExpect(status().isOk());
    }
}
