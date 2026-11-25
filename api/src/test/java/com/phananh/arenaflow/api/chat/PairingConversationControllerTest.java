package com.phananh.arenaflow.api.chat;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat dùng chung 2.16–2.20 với cuộc trò chuyện luồng B (thương lượng) qua HTTP thật (DB dev, rollback mỗi test):
 * nhắn được khi thương lượng NEGOTIATING, chỉ xem khi bị thay thế, gửi trên kèo quá hạn chuyển kèo + thương lượng
 * EXPIRED; đầu phòng và "Tin nhắn của tôi" trả thương lượng.
 */
class PairingConversationControllerTest extends ApiIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alphaCaptain;
    private User betaCaptain;
    private Team beta;
    private UUID matchId;
    private UUID pairingId;
    private UUID conversationId;

    @BeforeEach
    void openNegotiation() {
        alphaCaptain = saveUser("Minh");
        betaCaptain = saveUser("Nam");
        Team alpha = saveTeam("Alpha " + UUID.randomUUID(), alphaCaptain);
        beta = saveTeam("Beta " + UUID.randomUUID(), betaCaptain);
        flush();
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        LocalDateTime start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        matchId = fixtures.needVenueMatch(alpha.getId(), start, start.plusHours(2), start.minusHours(6));
        UUID venueId = fixtures.venue(alphaCaptain.getId(), "Sân X", "ACTIVE", AppTime.now().minusDays(1));
        fixtures.venueOption(matchId, venueId, 1);
        fixtures.timeOption(matchId, start, start.plusHours(2));
        pairingId = fixtures.pairing(matchId, beta.getId());
        conversationId = fixtures.conversation(matchId, alpha.getId(), beta.getId());
    }

    private String messagesPath() {
        return "/api/v1/conversations/" + conversationId + "/messages";
    }

    @Test
    void negotiatingTeamsChatAndHeaderAndMyListCarryPairing() throws Exception {
        mockMvc.perform(as(betaCaptain, post(messagesPath())).content("{\"content\":\"Sân X 18:00 nhé?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.senderPartyId").value(beta.getId().toString()));

        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchType").value("NEED_VENUE"))
                .andExpect(jsonPath("$.data.pairingId").value(pairingId.toString()))
                .andExpect(jsonPath("$.data.pairingStatus").value("NEGOTIATING"))
                .andExpect(jsonPath("$.data.requestId").doesNotExist())
                .andExpect(jsonPath("$.data.canSend").value(true));

        mockMvc.perform(as(betaCaptain, get("/api/v1/profile/conversations").param("pageSize", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.conversationId == '" + conversationId + "')].pairingId",
                        hasItem(pairingId.toString())));
    }

    @Test
    void supersededNegotiationIsReadOnly() throws Exception {
        jdbcTemplate.update("UPDATE pairing SET status = 'SUPERSEDED' WHERE id = ?", pairingId);

        mockMvc.perform(as(betaCaptain, post(messagesPath())).content("{\"content\":\"Còn không?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHAT_004"));
        mockMvc.perform(as(betaCaptain, get("/api/v1/conversations/" + conversationId)))
                .andExpect(jsonPath("$.data.pairingStatus").value("SUPERSEDED"))
                .andExpect(jsonPath("$.data.canSend").value(false));
    }

    @Test
    void sendingOnOverdueMatchExpiresMatchAndNegotiation() throws Exception {
        jdbcTemplate.update("UPDATE match SET expires_at = ? WHERE id = ?", AppTime.now().minusMinutes(1), matchId);

        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + conversationId)))
                .andExpect(jsonPath("$.data.pairingStatus").value("EXPIRED"));
        mockMvc.perform(as(betaCaptain, post(messagesPath())).content("{\"content\":\"Chốt nhé\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCH_004"));

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM match WHERE id = ?", String.class, matchId))
                .isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM pairing WHERE id = ?", String.class, pairingId))
                .isEqualTo("EXPIRED");
    }
}
