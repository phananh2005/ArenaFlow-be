package com.phananh.arenaflow.api.chat;

import com.jayway.jsonpath.JsonPath;
import com.phananh.arenaflow.api.ApiIntegrationTest;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.entity.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chat dùng chung 2.16–2.20 qua HTTP thật (DB dev, rollback mỗi test) với cuộc trò chuyện luồng A. */
class ConversationControllerTest extends ApiIntegrationTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alphaCaptain;
    private User betaCaptain;
    private User outsider;
    private Team alpha;
    private Team beta;
    private String conversationId;
    private String requestId;

    @BeforeEach
    void openConversation() throws Exception {
        alphaCaptain = saveUser("Minh");
        betaCaptain = saveUser("Nam");
        outsider = saveUser("Người ngoài");
        alpha = saveTeam("Alpha " + UUID.randomUUID(), alphaCaptain);
        beta = saveTeam("Beta " + UUID.randomUUID(), betaCaptain);
        flush();
        UUID sportId = jdbcTemplate.queryForObject("SELECT id FROM sport WHERE is_active ORDER BY name LIMIT 1", UUID.class);
        UUID venueId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân', 'Địa chỉ', 10.7, 106.7, 'USER', ?)", venueId, alphaCaptain.getId());
        LocalDateTime start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
        String match = mockMvc.perform(as(alphaCaptain, post("/api/v1/matches")).content("""
                        {"teamId":"%s","sportId":"%s","venueId":"%s","format":"FIVE_A_SIDE","startsAt":"%s",
                         "endsAt":"%s","expiresAt":"%s"}
                        """.formatted(alpha.getId(), sportId, venueId, start.format(ISO), start.plusHours(1).format(ISO),
                        start.minusHours(3).format(ISO))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String request = mockMvc.perform(as(betaCaptain, post("/api/v1/matches/" + JsonPath.read(match, "$.data.matchId")
                        + "/requests")).content("{\"teamId\":\"" + beta.getId() + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        conversationId = JsonPath.read(request, "$.data.conversationId");
        requestId = JsonPath.read(request, "$.data.requestId");
    }

    @Test
    void bothCaptainsChatWhileRequestPendingAndSeeMessagesInOrder() throws Exception {
        mockMvc.perform(as(betaCaptain, post("/api/v1/conversations/" + conversationId + "/messages"))
                        .content("{\"content\":\"  18:00 sân Y được không?  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.content").value("18:00 sân Y được không?"))
                .andExpect(jsonPath("$.data.senderPartyId").value(beta.getId().toString()))
                .andExpect(jsonPath("$.data.senderName").value("Nam"));
        mockMvc.perform(as(alphaCaptain, post("/api/v1/conversations/" + conversationId + "/messages"))
                        .content("{\"content\":\"OK\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + conversationId + "/messages").param("limit", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].content").value("OK"))
                .andExpect(jsonPath("$.data.hasMore").value(true));

        mockMvc.perform(as(alphaCaptain, post("/api/v1/conversations/" + conversationId + "/messages/read")))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parties[0].partyId").value(alpha.getId().toString()))
                .andExpect(jsonPath("$.data.requestId").value(requestId))
                .andExpect(jsonPath("$.data.requestStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.viewerPartyId").value(alpha.getId().toString()))
                .andExpect(jsonPath("$.data.canSend").value(true));

        mockMvc.perform(as(betaCaptain, get("/api/v1/profile/conversations")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].conversationId").value(conversationId))
                .andExpect(jsonPath("$.data[0].otherParty.partyId").value(alpha.getId().toString()))
                .andExpect(jsonPath("$.data[0].lastMessage.contentPreview").value("OK"))
                .andExpect(jsonPath("$.data[0].hasUnread").value(true));
    }

    @Test
    void outsiderAndRejectedTeamCannotSend() throws Exception {
        mockMvc.perform(as(outsider, get("/api/v1/conversations/" + conversationId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHAT_003"));

        mockMvc.perform(as(alphaCaptain, post("/api/v1/match-requests/" + requestId + "/reject")))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(betaCaptain, post("/api/v1/conversations/" + conversationId + "/messages"))
                        .content("{\"content\":\"Còn chỗ không?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CHAT_004"));
    }

    @Test
    void cursorValidationAndUnknownConversation() throws Exception {
        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + conversationId + "/messages")
                        .param("before", UUID.randomUUID().toString()).param("after", UUID.randomUUID().toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("after"));

        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + conversationId + "/messages")
                        .param("before", UUID.randomUUID().toString())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CHAT_001"));

        mockMvc.perform(as(alphaCaptain, get("/api/v1/conversations/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHAT_002"));
    }
}
