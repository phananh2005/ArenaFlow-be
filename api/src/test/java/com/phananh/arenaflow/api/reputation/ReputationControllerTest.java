package com.phananh.arenaflow.api.reputation;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.ReputationDbFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hợp đồng HTTP đầu-cuối của đánh giá / report / tranh chấp / uy tín: status, envelope, validation, quyền Admin.
 * Thời điểm dữ liệu mẫu đặt trong quá khứ xa để hạn tranh chấp tính theo đồng hồ thật vẫn đúng.
 */
class ReputationControllerTest extends ApiIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 10, 18, 0);

    @Autowired
    private JdbcTemplate jdbc;

    private ReputationDbFixtures fixtures;
    private User captainA;
    private User captainB;
    private Team teamA;
    private Team teamB;
    private UUID matchId;

    @BeforeEach
    void setUp() {
        fixtures = new ReputationDbFixtures(jdbc);
        captainA = saveUser("Captain A");
        captainB = saveUser("Captain B");
        teamA = saveTeam("Team A", captainA);
        teamB = saveTeam("Team B", captainB);
        flush();
        matchId = fixtures.match(teamA.getId(), teamB.getId(), "ARCHIVED", START, fixtures.venue(captainA.getId()));
    }

    @Test
    void firstRatingCompletesMatchAndReturns201WithoutLocation() throws Exception {
        mockMvc.perform(as(captainA, post("/api/v1/matches/{id}/ratings", matchId))
                        .content("{\"score\":5,\"comment\":\"Đá đẹp\",\"raterTeamScore\":3,\"rateeTeamScore\":2}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.data.matchStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.isNoShow").value(false))
                .andExpect(jsonPath("$.data.raterTeamId").value(teamA.getId().toString()));

        mockMvc.perform(as(captainA, post("/api/v1/matches/{id}/ratings", matchId)).content("{\"score\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RATING_002"));
    }

    @Test
    void ratingValidationErrorsPointToFields() throws Exception {
        mockMvc.perform(as(captainA, post("/api/v1/matches/{id}/ratings", matchId))
                        .content("{\"isNoShow\":true,\"score\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("score"));

        mockMvc.perform(as(captainA, post("/api/v1/matches/{id}/ratings", matchId)).content("{\"score\":4.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ratingsOfMatchHideReceivedUntilOwnRating() throws Exception {
        fixtures.rating(matchId, teamB.getId(), teamA.getId(), 4, false, captainB.getId());

        mockMvc.perform(as(captainA, get("/api/v1/matches/{id}/ratings", matchId)).param("teamId", teamA.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.given").doesNotExist())
                .andExpect(jsonPath("$.data.received").doesNotExist());

        mockMvc.perform(as(captainB, get("/api/v1/matches/{id}/ratings", matchId)).param("teamId", teamA.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));
    }

    @Test
    void reportThenDisputeThenAdminRejectRefundsPenalty() throws Exception {
        mockMvc.perform(as(captainA, post("/api/v1/matches/{id}/reports", matchId))
                        .content("{\"reportType\":\"NOSHOW\",\"reason\":\"Không đến\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.direction").value("SENT"))
                .andExpect(jsonPath("$.data.reliabilityPenalty").doesNotExist());
        flush();

        mockMvc.perform(as(captainB, get("/api/v1/teams/{id}/reports", teamB.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reliabilityPenalty").value(5))
                .andExpect(jsonPath("$.data[0].canDispute").value(true))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
        UUID reportId = jdbc.queryForObject("SELECT id FROM report WHERE match_id = ?", UUID.class, matchId);

        mockMvc.perform(as(captainB, post("/api/v1/reports/{id}/dispute", reportId)).content("{\"evidence\":\"Có mặt\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.reportStatus").value("DISPUTED"));
        flush();
        UUID disputeId = jdbc.queryForObject("SELECT id FROM dispute WHERE report_id = ?", UUID.class, reportId);

        User admin = saveUser("Admin", User.Role.ADMIN);
        mockMvc.perform(as(captainA, get("/api/v1/admin/disputes")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
        mockMvc.perform(as(admin, patch("/api/v1/admin/disputes/{id}/resolve", disputeId))
                        .content("{\"decision\":\"REJECTED\",\"note\":\"Có mặt đúng giờ\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESOLVED"))
                .andExpect(jsonPath("$.data.reportedTeamReliabilityScore").value(80));
        mockMvc.perform(as(admin, patch("/api/v1/admin/disputes/{id}/resolve", disputeId))
                        .content("{\"decision\":\"REJECTED\",\"note\":\"Lần hai\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DISPUTE_002"));
    }

    @Test
    void reportListRejectsUnknownDirectionAndReputationIsPublic() throws Exception {
        mockMvc.perform(as(captainA, get("/api/v1/teams/{id}/reports", teamA.getId())).param("direction", "BOTH"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(as(captainB, get("/api/v1/teams/{id}/reputation", teamA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reliabilityScore").value(80))
                .andExpect(jsonPath("$.data.reliabilityLevel").value("NORMAL"))
                .andExpect(jsonPath("$.data.completion.isNewcomer").value(true))
                .andExpect(jsonPath("$.data.verification.isVerified").value(false));

        mockMvc.perform(as(captainB, get("/api/v1/teams/{id}/reputation", UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_001"));
    }

    @Test
    void chatReportValidatesReasonAndMissingConversation() throws Exception {
        mockMvc.perform(as(captainA, post("/api/v1/conversations/{id}/reports", UUID.randomUUID()))
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("reason"));

        mockMvc.perform(as(captainA, post("/api/v1/conversations/{id}/reports", UUID.randomUUID()))
                        .content("{\"reason\":\"Chửi bới\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_002"));
    }
}
