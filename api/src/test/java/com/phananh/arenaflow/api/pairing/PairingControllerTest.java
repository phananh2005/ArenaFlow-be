package com.phananh.arenaflow.api.pairing;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.fixture.PairingFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Hợp đồng HTTP của thương lượng luồng B (mã thành công, envelope, 400/401/403/409). Chạy trên DB dev. */
class PairingControllerTest extends ApiIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void createPairingReturns201WithoutLocationAndConversation() throws Exception {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        LocalDateTime now = AppTime.now();
        PairingFixtures.Scenario s = fixtures.scenario(now);
        UUID gammaCaptain = fixtures.user("Gamma Captain");
        UUID gamma = fixtures.activeTeam("Gamma", gammaCaptain);

        mockMvc.perform(json(as(user(gammaCaptain), post("/api/v1/matches/{id}/pairings", s.matchId())),
                        "{\"teamId\":\"%s\"}".formatted(gamma)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.data.status").value("NEGOTIATING"))
                .andExpect(jsonPath("$.data.revision").value(0))
                .andExpect(jsonPath("$.data.conversationId").isString())
                .andExpect(jsonPath("$.data.proposal").doesNotExist())
                .andExpect(jsonPath("$.data.costAgreement.customItems").isEmpty());
    }

    @Test
    void detailListsAndWritesFollowContract() throws Exception {
        PairingFixtures.Scenario s = new PairingFixtures(jdbcTemplate).scenario(AppTime.now());
        User author = user(s.authorCaptain());
        User interested = user(s.interestedCaptain());

        mockMvc.perform(as(interested, get("/api/v1/pairings/{id}", s.pairingId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value(s.conversationId().toString()))
                .andExpect(jsonPath("$.data.proposal.venueName").value("Sân X"))
                .andExpect(jsonPath("$.data.authorTeam.captainName").value("Đội trưởng A"));
        mockMvc.perform(as(author, get("/api/v1/matches/{id}/pairings", s.matchId())).param("status", "NEGOTIATING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].pairingId").value(s.pairingId().toString()))
                .andExpect(jsonPath("$.pagination.totalItems").value(1));
        mockMvc.perform(as(interested, get("/api/v1/teams/{id}/pairings", s.interestedTeam())).param("role", "INTERESTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conversationId").value(s.conversationId().toString()));

        mockMvc.perform(json(as(author, put("/api/v1/pairings/{id}/cost-agreement", s.pairingId())),
                        "{\"teamId\":\"%s\",\"revision\":0,\"depositAmount\":200000,\"depositHolder\":\"Chủ nhà\"}"
                                .formatted(s.authorTeam())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision").value(1))
                .andExpect(jsonPath("$.data.costAgreement.depositAmount").value(200000));
        mockMvc.perform(json(as(interested, post("/api/v1/pairings/{id}/confirm", s.pairingId())),
                        "{\"teamId\":\"%s\",\"revision\":0}".formatted(s.interestedTeam())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PAIRING_006"));
        mockMvc.perform(json(as(interested, post("/api/v1/pairings/{id}/confirm", s.pairingId())),
                        "{\"teamId\":\"%s\",\"revision\":1}".formatted(s.interestedTeam())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEGOTIATING"));
        mockMvc.perform(json(as(author, post("/api/v1/pairings/{id}/confirm", s.pairingId())),
                        "{\"teamId\":\"%s\",\"revision\":1}".formatted(s.authorTeam())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.matchStatus").value("CONFIRMED"));
    }

    @Test
    void rejectsInvalidInputOutsiderAndMissingToken() throws Exception {
        PairingFixtures fixtures = new PairingFixtures(jdbcTemplate);
        PairingFixtures.Scenario s = fixtures.scenario(AppTime.now());
        User interested = user(s.interestedCaptain());
        User outsider = user(fixtures.user("Người ngoài"));

        mockMvc.perform(as(interested, get("/api/v1/pairings/not-a-uuid")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(interested, get("/api/v1/teams/{id}/pairings", s.interestedTeam())).param("status", "FOO"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(as(interested, put("/api/v1/pairings/{id}/proposal", s.pairingId())),
                        "{\"teamId\":\"%s\",\"revision\":-1}".formatted(s.interestedTeam())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(as(outsider, get("/api/v1/pairings/{id}", s.pairingId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PAIRING_002"));
        mockMvc.perform(get("/api/v1/pairings/{id}", s.pairingId()))
                .andExpect(status().isUnauthorized());
    }

    private User user(UUID id) {
        return userRepository.findById(id).orElseThrow();
    }
}
