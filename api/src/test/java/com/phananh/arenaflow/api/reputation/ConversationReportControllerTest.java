package com.phananh.arenaflow.api.reputation;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.repository.ReputationDbFixtures;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Report "giao tiếp tệ" qua chat của kèo (2.10) qua HTTP thật trên DB dev, rollback mỗi test, với lõi chat thật:
 * mọi trạng thái yêu cầu / thương lượng / kèo có ít nhất một tin nhắn đều report được; các mã chặn theo người gọi,
 * đội và tin nhắn; chống trùng với report sau trận cùng chiều.
 */
class ConversationReportControllerTest extends ApiIntegrationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 1, 10, 18, 0);
    private static final LocalDateTime EARLIER = START.minusDays(1);
    private static final String REASON = "{\"reason\":\"Chửi bới trong chat\"}";

    @Autowired
    private JdbcTemplate jdbc;

    private ReputationDbFixtures fixtures;
    private PairingFixtures pairingFixtures;
    private User alphaCaptain;
    private User betaCaptain;
    private Team alpha;
    private Team beta;
    private Team gamma;
    private UUID venueId;

    @BeforeEach
    void setUp() {
        fixtures = new ReputationDbFixtures(jdbc);
        pairingFixtures = new PairingFixtures(jdbc);
        alphaCaptain = saveUser("Minh");
        betaCaptain = saveUser("Nam");
        alpha = saveTeam("Alpha " + UUID.randomUUID(), alphaCaptain);
        beta = saveTeam("Beta " + UUID.randomUUID(), betaCaptain);
        gamma = saveTeam("Gamma " + UUID.randomUUID(), saveUser("Lan"));
        flush();
        venueId = fixtures.venue(alphaCaptain.getId());
    }

    @ParameterizedTest(name = "luồng A: yêu cầu {0}, kèo {1}")
    @CsvSource({
            "PENDING, OPEN",
            "REJECTED, OPEN",
            "WITHDRAWN, OPEN",
            "EXPIRED, EXPIRED",
            "SUPERSEDED, CONFIRMED",
            "ACCEPTED, CONFIRMED",
            "ACCEPTED, CANCELLED",
            "ACCEPTED, COMPLETED"
    })
    void flowAAnyRequestAndMatchStatusWithMessageCanBeReported(String requestStatus, String matchStatus)
            throws Exception {
        UUID conversationId = flowAConversation(requestStatus, matchStatus);
        message(conversationId);

        report(alphaCaptain, conversationId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.direction").value("SENT"))
                .andExpect(jsonPath("$.data.reportType").value("BAD_COMMUNICATION"))
                .andExpect(jsonPath("$.data.counterpartTeam.teamId").value(beta.getId().toString()))
                .andExpect(jsonPath("$.data.reliabilityPenalty").doesNotExist());
    }

    @ParameterizedTest(name = "luồng B: thương lượng {0}, kèo {1}")
    @CsvSource({
            "NEGOTIATING, OPEN",
            "SUPERSEDED, CONFIRMED",
            "EXPIRED, EXPIRED",
            "CONFIRMED, CONFIRMED"
    })
    void flowBAnyPairingAndMatchStatusWithMessageCanBeReported(String pairingStatus, String matchStatus)
            throws Exception {
        UUID conversationId = flowBConversation(pairingStatus, matchStatus);
        message(conversationId);

        report(betaCaptain, conversationId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.counterpartTeam.teamId").value(alpha.getId().toString()));
    }

    @Test
    void conversationWithoutMessagesCannotBeReported() throws Exception {
        UUID conversationId = flowAConversation("PENDING", "OPEN");

        report(alphaCaptain, conversationId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REPORT_010"));
    }

    @Test
    void chatReportBlocksSameDirectionAfterMatchReportButNotOppositeDirection() throws Exception {
        UUID conversationId = flowAConversation("ACCEPTED", "COMPLETED");
        message(conversationId);
        UUID matchId = matchOf(conversationId);

        report(alphaCaptain, conversationId).andExpect(status().isCreated());
        mockMvc.perform(as(alphaCaptain, post("/api/v1/matches/{id}/reports", matchId))
                        .content("{\"reportType\":\"BAD_COMMUNICATION\",\"reason\":\"Sau trận\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_004"));
        report(betaCaptain, conversationId).andExpect(status().isCreated());

        mockMvc.perform(as(alphaCaptain, get("/api/v1/teams/{id}/reports", alpha.getId()).param("direction", "SENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
        mockMvc.perform(as(betaCaptain, get("/api/v1/teams/{id}/reports", beta.getId()).param("direction", "RECEIVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].reportType").value("BAD_COMMUNICATION"))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
    }

    @Test
    void outsiderIsNotAParticipant() throws Exception {
        UUID conversationId = flowAConversation("PENDING", "OPEN");
        message(conversationId);

        report(saveUser("Người ngoài"), conversationId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_003"));
    }

    @Test
    void deletedAccountIsRejectedFirst() throws Exception {
        UUID conversationId = flowAConversation("PENDING", "OPEN");
        message(conversationId);
        fixtures.deleteUser(alphaCaptain.getId(), EARLIER);

        report(alphaCaptain, conversationId)
                .andExpect(jsonPath("$.error.code").value("USER_001"));
    }

    @Test
    void captainOfBothTeamsCannotReport() throws Exception {
        UUID conversationId = flowAConversation("PENDING", "OPEN");
        message(conversationId);
        jdbc.update("UPDATE team_member SET user_id = ? WHERE team_id = ? AND role = 'CAPTAIN' AND status = 'ACTIVE'",
                alphaCaptain.getId(), beta.getId());

        report(alphaCaptain, conversationId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REPORT_005"));
    }

    @Test
    void inactiveCallerTeamCannotReport() throws Exception {
        UUID conversationId = flowAConversation("PENDING", "OPEN");
        message(conversationId);
        fixtures.teamStatus(alpha.getId(), "INACTIVE");

        report(alphaCaptain, conversationId)
                .andExpect(jsonPath("$.error.code").value("TEAM_015"));
    }

    @Test
    void disbandedReportedTeamCanStillBeReportedButItsDisbanderCannotReport() throws Exception {
        UUID conversationId = flowAConversation("ACCEPTED", "CONFIRMED");
        message(conversationId);
        disband(beta, betaCaptain);

        report(alphaCaptain, conversationId).andExpect(status().isCreated());
        report(betaCaptain, conversationId)
                .andExpect(jsonPath("$.error.code").value("TEAM_016"));
    }

    private ResultActions report(User caller, UUID conversationId) throws Exception {
        return mockMvc.perform(as(caller, post("/api/v1/conversations/{id}/reports", conversationId)).content(REASON));
    }

    /** Kèo luồng A của Alpha, yêu cầu của Beta ở {@code requestStatus}; kèo đã ghép → đối thủ là Beta (SUPERSEDED → Gamma). */
    private UUID flowAConversation(String requestStatus, String matchStatus) {
        UUID opponent = switch (matchStatus) {
            case "OPEN", "EXPIRED" -> null;
            default -> "SUPERSEDED".equals(requestStatus) ? gamma.getId() : beta.getId();
        };
        String insertedStatus = "CANCELLED".equals(matchStatus) ? "CONFIRMED" : matchStatus;
        UUID matchId = fixtures.match(alpha.getId(), opponent, insertedStatus, START, venueId);
        if ("CANCELLED".equals(matchStatus)) {
            fixtures.cancelMatch(matchId, alpha.getId(), EARLIER);
        }
        jdbc.update("""
                INSERT INTO match_request (id, match_id, requester_team_id, status, requested_at, responded_at,
                                           withdrawn_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), matchId, beta.getId(), requestStatus, EARLIER.minusDays(1),
                "REJECTED".equals(requestStatus) ? EARLIER : null, "WITHDRAWN".equals(requestStatus) ? EARLIER : null);
        return pairingFixtures.conversation(matchId, alpha.getId(), beta.getId());
    }

    /** Kèo luồng B của Alpha, thương lượng của Beta ở {@code pairingStatus}; kèo đã ghép ghi địa điểm đã chốt. */
    private UUID flowBConversation(String pairingStatus, String matchStatus) {
        UUID matchId = pairingFixtures.needVenueMatch(alpha.getId(), START, START.plusHours(2), START.minusHours(2));
        pairingFixtures.venueOption(matchId, venueId, 1);
        UUID timeOptionId = pairingFixtures.timeOption(matchId, START, START.plusHours(2));
        UUID pairingId = pairingFixtures.pairing(matchId, beta.getId());
        pairingFixtures.proposal(pairingId, venueId, timeOptionId);
        if ("CONFIRMED".equals(pairingStatus)) {
            jdbc.update("""
                    UPDATE pairing SET status = 'CONFIRMED', author_confirmed_at = ?, interested_confirmed_at = ?,
                                       final_confirmed_at = ?
                    WHERE id = ?
                    """, EARLIER, EARLIER, EARLIER, pairingId);
        } else {
            jdbc.update("UPDATE pairing SET status = ? WHERE id = ?", pairingStatus, pairingId);
        }
        if ("CONFIRMED".equals(matchStatus)) {
            UUID opponent = "CONFIRMED".equals(pairingStatus) ? beta.getId() : gamma.getId();
            jdbc.update("UPDATE match SET status = 'CONFIRMED', confirmed_opponent_team_id = ?, venue_id = ? WHERE id = ?",
                    opponent, venueId, matchId);
        } else {
            jdbc.update("UPDATE match SET status = ? WHERE id = ?", matchStatus, matchId);
        }
        return pairingFixtures.conversation(matchId, alpha.getId(), beta.getId());
    }

    private void message(UUID conversationId) {
        jdbc.update("""
                INSERT INTO conversation_message (id, conversation_id, sender_party, sender_user_id, message)
                VALUES (?, ?, 'B', ?, 'Xin chào')
                """, UUID.randomUUID(), conversationId, betaCaptain.getId());
    }

    private UUID matchOf(UUID conversationId) {
        return jdbc.queryForObject("SELECT match_id FROM conversation WHERE id = ?", UUID.class, conversationId);
    }

    /** Giải tán như USER-002: không còn đội trưởng ACTIVE, ghi người bấm giải tán. */
    private void disband(Team team, User by) {
        LocalDateTime now = AppTime.now();
        jdbc.update("UPDATE team_member SET status = 'LEFT', left_at = ? WHERE team_id = ? AND status = 'ACTIVE'",
                now, team.getId());
        jdbc.update("""
                UPDATE team SET status = 'DISBANDED', disband_reason = 'Giải tán', disbanded_at = ?, disbanded_by = ?
                WHERE id = ?
                """, now, by.getId(), team.getId());
    }
}
