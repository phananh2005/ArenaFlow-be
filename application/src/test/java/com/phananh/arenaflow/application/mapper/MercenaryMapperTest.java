package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.RecruitmentResponse;
import com.phananh.arenaflow.domain.projection.RecruitmentView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MercenaryMapperTest {

    private final MercenaryMapper mapper = Mappers.getMapper(MercenaryMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    @ParameterizedTest
    @CsvSource({"0.01, 0.5", "0.5, 0.5", "0.51, 1.0", "2.26, 2.5", "3.0, 3.0"})
    void distanceIsRoundedUpToHalfKilometre(double raw, String expected) {
        assertThat(MercenaryMapper.roundDistance(raw)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void nullDistanceStaysNull() {
        assertThat(MercenaryMapper.roundDistance(null)).isNull();
    }

    @Test
    void searcherWithoutApprovedApplicationSeesAreaButNotVenueNorCaptain() {
        RecruitmentResponse response = mapper.toResponse(view(Map.of("viewerIsCaptain", false)));

        assertThat(response.match().venue()).isNull();
        assertThat(response.captainName()).isNull();
        assertThat(response.match().area().wardName()).isEqualTo("Phường 1");
        assertThat(response.viewerRole()).isNull();
        assertThat(response.myApplication()).isNull();
        assertThat(response.pendingApplicationCount()).isNull();
        assertThat(response.match().source()).isEqualTo("SYSTEM");
    }

    @Test
    void approvedApplicantSeesVenueAndCaptain() {
        UUID applicationId = UUID.randomUUID();
        RecruitmentResponse response = mapper.toResponse(view(Map.of("viewerIsCaptain", false,
                "myApplicationId", applicationId, "myApplicationStatus", "APPROVED")));

        assertThat(response.match().venue().name()).isEqualTo("Sân A");
        assertThat(response.captainName()).isEqualTo("Captain");
        assertThat(response.viewerRole()).isEqualTo("APPLICANT");
        assertThat(response.myApplication().applicationId()).isEqualTo(applicationId);
    }

    @Test
    void pendingApplicationOfClosedRecruitmentIsShownAsRejectedAndHidden() {
        RecruitmentResponse response = mapper.toResponse(view(Map.of("viewerIsCaptain", false,
                "myApplicationId", UUID.randomUUID(), "myApplicationStatus", "PENDING", "effectiveStatus", "CLOSED")));

        assertThat(response.status()).isEqualTo("CLOSED");
        assertThat(response.myApplication()).isNull();
        assertThat(response.viewerRole()).isNull();
        assertThat(response.match().venue()).isNull();
    }

    @Test
    void captainSeesEverythingAndPendingCount() {
        RecruitmentResponse response = mapper.toResponse(view(Map.of("viewerIsCaptain", true, "pendingCount", 3L)));

        assertThat(response.viewerRole()).isEqualTo("CAPTAIN");
        assertThat(response.match().venue()).isNotNull();
        assertThat(response.captainName()).isEqualTo("Captain");
        assertThat(response.pendingApplicationCount()).isEqualTo(3);
        assertThat(response.rejectedApplicationCount()).isNull();
    }

    @Test
    void deletedCaptainNameIsMasked() {
        RecruitmentResponse response = mapper.toResponse(view(Map.of("viewerIsCaptain", true, "captainDeleted", true)));

        assertThat(response.captainName()).isEqualTo("Người dùng đã xóa");
    }

    @Test
    void externalMatchSourceAndMissingWardGiveNullArea() {
        UUID externalId = UUID.randomUUID();
        RecruitmentResponse response = mapper.toResponse(view(Map.of("viewerIsCaptain", true,
                "source", "EXTERNAL", "externalMatchId", externalId, "wardName", "")));

        assertThat(response.match().source()).isEqualTo("EXTERNAL");
        assertThat(response.match().externalMatchId()).isEqualTo(externalId);
        assertThat(response.match().area()).isNull();
    }

    private RecruitmentView view(Map<String, Object> overrides) {
        LocalDateTime start = LocalDateTime.of(2026, 11, 22, 18, 0);
        Map<String, Object> values = new HashMap<>();
        values.put("recruitmentId", UUID.randomUUID());
        values.put("matchId", UUID.randomUUID());
        values.put("teamId", UUID.randomUUID());
        values.put("teamName", "FC A");
        values.put("teamStatus", "ACTIVE");
        values.put("position", "Tiền vệ");
        values.put("quantityNeeded", 2);
        values.put("quantityApproved", 1);
        values.put("status", "OPEN");
        values.put("effectiveStatus", "OPEN");
        values.put("source", "SYSTEM");
        values.put("gameStatus", "CONFIRMED");
        values.put("gameStart", start);
        values.put("gameEnd", start.plusMinutes(90));
        values.put("skillLevels", "[]");
        values.put("venueId", UUID.randomUUID());
        values.put("venueName", "Sân A");
        values.put("venueAddress", "1 Đường A");
        values.put("venueLatitude", new BigDecimal("10.7"));
        values.put("venueLongitude", new BigDecimal("106.7"));
        values.put("wardName", "Phường 1");
        values.put("provinceName", "TP HCM");
        values.put("captainName", "Captain");
        values.put("captainDeleted", false);
        values.put("pendingCount", 0L);
        values.put("createdAt", start.minusDays(1));
        values.put("updatedAt", start.minusDays(1));
        values.putAll(overrides);
        if ("".equals(values.get("wardName"))) {
            values.put("wardName", null);
            values.put("provinceName", null);
        }
        return projections.createProjection(RecruitmentView.class, values);
    }
}
