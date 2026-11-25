package com.phananh.arenaflow.api.match;

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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kèo qua HTTP thật (DB dev, rollback mỗi test): luồng A tạo, tìm, xem, chi phí, yêu cầu, chấp nhận, huỷ; luồng B tạo,
 * tìm, xem, huỷ.
 */
class MatchControllerTest extends ApiIntegrationTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User alphaCaptain;
    private User betaCaptain;
    private Team alpha;
    private Team beta;
    private UUID sportId;
    private UUID venueId;
    private LocalDateTime start;

    @BeforeEach
    void setUpFixtures() {
        alphaCaptain = saveUser("Minh");
        betaCaptain = saveUser("Nam");
        alpha = saveTeam("Alpha " + UUID.randomUUID(), alphaCaptain);
        beta = saveTeam("Beta " + UUID.randomUUID(), betaCaptain);
        flush();
        sportId = jdbcTemplate.queryForObject("SELECT id FROM sport WHERE is_active ORDER BY name LIMIT 1", UUID.class);
        venueId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO venue (id, name, address, latitude, longitude, created_by_type, created_by_user_id) "
                + "VALUES (?, 'Sân Hoa Lư', '2 Đinh Tiên Hoàng', 10.7881234, 106.6991234, 'USER', ?)", venueId,
                alphaCaptain.getId());
        start = AppTime.now().plusDays(2).truncatedTo(ChronoUnit.HOURS);
    }

    private String createBody(String type) {
        return """
                {"type":"%s","teamId":"%s","sportId":"%s","venueId":"%s","format":"SEVEN_A_SIDE",
                 "startsAt":"%s","endsAt":"%s","expiresAt":"%s","skillLevels":["Nhiệt tình"],"ageNote":" U23 "}
                """.formatted(type, alpha.getId(), sportId, venueId, start.format(ISO), start.plusHours(2).format(ISO),
                start.minusHours(6).format(ISO));
    }

    private String createMatch() throws Exception {
        String body = mockMvc.perform(as(alphaCaptain, post("/api/v1/matches")).content(createBody("HAS_VENUE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.ageNote").value("U23"))
                .andExpect(jsonPath("$.data.venue.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.data.venueOptions").isEmpty())
                .andExpect(jsonPath("$.data.costAgreement.customItems").isEmpty())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.matchId");
    }

    @Test
    void createSearchAndViewMatchWithLocalTimeWithoutOffset() throws Exception {
        String matchId = createMatch();

        String detail = mockMvc.perform(as(betaCaptain, get("/api/v1/matches/" + matchId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String startsAt = JsonPath.read(detail, "$.data.startsAt");
        assertThat(startsAt).isEqualTo(start.format(ISO)).doesNotContain("Z").doesNotContain("+");

        mockMvc.perform(as(betaCaptain, get("/api/v1/matches").param("sportId", sportId.toString())
                        .param("startsAtFrom", start.minusHours(1).format(ISO)).param("pageSize", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].matchId", hasItem(matchId)));

        mockMvc.perform(as(betaCaptain, get("/api/v1/matches").param("lat", "10.7881234").param("lng", "106.6991234")
                        .param("radiusKm", "1").param("pageSize", "100")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].matchId", hasItem(matchId)));

        mockMvc.perform(as(betaCaptain, get("/api/v1/teams/" + alpha.getId() + "/matches").param("role", "AUTHOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].matchId").value(matchId));
    }

    private String needVenueBody() {
        return """
                {"type":"NEED_VENUE","teamId":"%s","sportId":"%s","venueOptionIds":["%s"],"format":"SEVEN_A_SIDE",
                 "timeOptions":[{"startsAt":"%s","endsAt":"%s"},{"startsAt":"%s","endsAt":"%s"}],
                 "expiresAt":"%s","skillLevels":[]}
                """.formatted(alpha.getId(), sportId, venueId, start.plusHours(3).format(ISO),
                start.plusHours(5).format(ISO), start.format(ISO), start.plusHours(2).format(ISO),
                start.minusHours(6).format(ISO));
    }

    @Test
    void needVenueCreatesMatchWithOptionsAndRejectsFlowAFields() throws Exception {
        String created = mockMvc.perform(as(alphaCaptain, post("/api/v1/matches")).content(needVenueBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("NEED_VENUE"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.venue").doesNotExist())
                .andExpect(jsonPath("$.data.startsAt").value(start.format(ISO)))
                .andExpect(jsonPath("$.data.venueOptions[0].venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.data.timeOptions.length()").value(2))
                .andExpect(jsonPath("$.data.timeOptions[0].startsAt").value(start.format(ISO)))
                .andExpect(jsonPath("$.data.timeOptions[0].timeOptionId").isNotEmpty())
                .andExpect(jsonPath("$.data.costAgreement.customItems").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String matchId = JsonPath.read(created, "$.data.matchId");

        mockMvc.perform(as(betaCaptain, get("/api/v1/matches/" + matchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.venue").doesNotExist())
                .andExpect(jsonPath("$.data.venueOptions[0].venueName").value("Sân Hoa Lư"))
                .andExpect(jsonPath("$.data.timeOptions.length()").value(2))
                .andExpect(jsonPath("$.data.timeOptions[1].startsAt").value(start.plusHours(3).format(ISO)))
                .andExpect(jsonPath("$.data.confirmedPairingId").doesNotExist());

        String search = mockMvc.perform(as(betaCaptain, get("/api/v1/matches").param("type", "NEED_VENUE")
                        .param("startsAtFrom", start.plusHours(2).format(ISO)).param("lat", "10.7881234")
                        .param("lng", "106.6991234").param("radiusKm", "1").param("pageSize", "100")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(search, "$.data[?(@.matchId == '" + matchId + "')].venueOptionCount"))
                .containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(search, "$.data[?(@.matchId == '" + matchId + "')].timeOptionCount"))
                .containsExactly(2);

        mockMvc.perform(as(alphaCaptain, post("/api/v1/matches")).content(needVenueBody()
                        .replace("\"venueOptionIds\"", "\"venueId\":\"" + venueId + "\",\"venueOptionIds\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("venueId"));
    }

    @Test
    void offsetTimeQueryIsValidationError() throws Exception {
        mockMvc.perform(as(betaCaptain, get("/api/v1/matches").param("startsAtFrom", "2026-11-12T00:00:00Z")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[0].field").value("startsAtFrom"));
    }

    @Test
    void costAgreementKeepsOrderAndRequiresAuthorCaptain() throws Exception {
        String matchId = createMatch();
        String body = """
                {"costType":"BOTH","costSplit":"FIFTY_FIFTY","venueAmount":600000,"drinksAmount":100000,
                 "customItems":[{"itemName":"Trọng tài","amount":200000},{"itemName":"Áo bib","amount":50000}]}
                """;

        mockMvc.perform(as(betaCaptain, put("/api/v1/matches/" + matchId + "/cost-agreement")).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_002"));

        mockMvc.perform(as(alphaCaptain, put("/api/v1/matches/" + matchId + "/cost-agreement")).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customItems[0].itemName").value("Trọng tài"))
                .andExpect(jsonPath("$.data.customItems[1].itemName").value("Áo bib"));

        mockMvc.perform(as(alphaCaptain, get("/api/v1/matches/" + matchId)))
                .andExpect(jsonPath("$.data.costAgreement.customItems.length()").value(2))
                .andExpect(jsonPath("$.data.costAgreement.depositAmount").doesNotExist());
    }

    @Test
    void requestAcceptAndCancelFlowOpensConversationAndPenalisesCancellingTeam() throws Exception {
        String matchId = createMatch();

        String request = mockMvc.perform(as(betaCaptain, post("/api/v1/matches/" + matchId + "/requests"))
                        .content("{\"teamId\":\"" + beta.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.conversationId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String requestId = JsonPath.read(request, "$.data.requestId");

        mockMvc.perform(as(alphaCaptain, get("/api/v1/matches/" + matchId + "/requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].requestId").value(requestId));

        mockMvc.perform(as(alphaCaptain, post("/api/v1/match-requests/" + requestId + "/accept")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.opponentTeamId").value(beta.getId().toString()));

        mockMvc.perform(as(alphaCaptain, post("/api/v1/matches/" + matchId + "/cancel"))
                        .content("{\"teamId\":\"" + alpha.getId() + "\",\"expectedStatus\":\"OPEN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCH_015"));

        mockMvc.perform(as(betaCaptain, post("/api/v1/match-requests/" + requestId + "/withdraw"))
                        .content("{\"expectedStatus\":\"ACCEPTED\",\"reason\":\"Không đủ người\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.reliabilityPenalty").value(-2));

        flush();
        Integer score = jdbcTemplate.queryForObject("SELECT reliability_score FROM team WHERE id = ?", Integer.class,
                beta.getId());
        assertThat(score).isEqualTo(78);

        mockMvc.perform(as(betaCaptain, get("/api/v1/teams/" + beta.getId() + "/match-requests")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.data[0].authorTeamId").value(alpha.getId().toString()));
    }

    @Test
    void duplicateMatchRequestReturnsMatch006() throws Exception {
        String matchId = createMatch();

        mockMvc.perform(as(betaCaptain, post("/api/v1/matches/" + matchId + "/requests"))
                        .content("{\"teamId\":\"" + beta.getId() + "\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(as(betaCaptain, post("/api/v1/matches/" + matchId + "/requests"))
                        .content("{\"teamId\":\"" + beta.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("MATCH_006"));
    }

    @Test
    void rejectWithoutBodyReturnsNoContent() throws Exception {
        String matchId = createMatch();
        String request = mockMvc.perform(as(betaCaptain, post("/api/v1/matches/" + matchId + "/requests"))
                        .content("{\"teamId\":\"" + beta.getId() + "\"}"))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(as(alphaCaptain, post("/api/v1/match-requests/" + JsonPath.read(request, "$.data.requestId")
                        + "/reject")))
                .andExpect(status().isNoContent());
    }
}
