package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.CostCustomItemResponse;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MatchMapperTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 18, 0);

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final MatchMapper mapper = Mappers.getMapper(MatchMapper.class);
    private final UUID matchId = UUID.randomUUID();
    private final UUID authorTeamId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();

    private Map<String, Object> detailRow() {
        Map<String, Object> row = new HashMap<>();
        row.put("matchId", matchId);
        row.put("type", "HAS_VENUE");
        row.put("effectiveStatus", "EXPIRED");
        row.put("sportId", UUID.randomUUID());
        row.put("sportName", "Bóng đá");
        row.put("format", "SEVEN_A_SIDE");
        row.put("skillLevels", "[\"Nhiệt tình\"]");
        row.put("matchStartTime", START);
        row.put("matchEndTime", START.plusHours(2));
        row.put("expiresAt", START.minusHours(6));
        row.put("authorTeamId", authorTeamId);
        row.put("authorTeamName", "Team Alpha");
        row.put("authorTeamReliabilityScore", 80);
        row.put("venueId", venueId);
        row.put("venueName", "Sân Hoa Lư");
        row.put("venueAddress", "2 Đinh Tiên Hoàng");
        row.put("venueLatitude", new BigDecimal("10.7881234"));
        row.put("venueLongitude", new BigDecimal("106.6991234"));
        row.put("venueStatus", "ACTIVE");
        row.put("costType", "BOTH");
        row.put("costSplit", "FIFTY_FIFTY");
        row.put("costVenueAmount", 600_000L);
        row.put("costDrinksAmount", 100_000L);
        row.put("createdAt", START.minusDays(3));
        row.put("updatedAt", START.minusDays(2));
        return row;
    }

    @Test
    void detailMapsTeamsVenueCostAgreementAndEmptyFlowBFields() {
        MatchDetailView view = projections.createProjection(MatchDetailView.class, detailRow());
        CostCustomItem item = CostCustomItem.builder().id(UUID.randomUUID()).itemName("Trọng tài").amount(200_000L)
                .displayOrder(1).build();

        MatchResponse response = mapper.toResponse(view, List.of(item));

        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.status()).isEqualTo("EXPIRED");
        assertThat(response.skillLevels()).containsExactly("Nhiệt tình");
        assertThat(response.startsAt()).isEqualTo(START);
        assertThat(response.endsAt()).isEqualTo(START.plusHours(2));
        assertThat(response.authorTeam().teamId()).isEqualTo(authorTeamId);
        assertThat(response.authorTeam().reliabilityScore()).isEqualTo(80);
        assertThat(response.opponentTeam()).isNull();
        assertThat(response.venue().venueId()).isEqualTo(venueId);
        assertThat(response.venue().venueStatus()).isEqualTo("ACTIVE");
        assertThat(response.venueOptions()).isEmpty();
        assertThat(response.timeOptions()).isEmpty();
        assertThat(response.confirmedPairingId()).isNull();
        assertThat(response.costAgreement().costType()).isEqualTo("BOTH");
        assertThat(response.costAgreement().depositAmount()).isNull();
        assertThat(response.costAgreement().customItems())
                .containsExactly(new CostCustomItemResponse(item.getId(), "Trọng tài", 200_000L));
    }

    @Test
    void detailWithoutVenueOrCostHasNullVenueAndEmptyAgreement() {
        Map<String, Object> row = detailRow();
        row.remove("venueId");
        row.remove("costType");
        row.remove("costSplit");
        row.remove("costVenueAmount");
        row.remove("costDrinksAmount");
        row.put("opponentTeamId", UUID.randomUUID());
        row.put("opponentTeamName", "Team Beta");
        row.put("opponentTeamReliabilityScore", 75);

        MatchResponse response = mapper.toResponse(projections.createProjection(MatchDetailView.class, row), List.of());

        assertThat(response.venue()).isNull();
        assertThat(response.opponentTeam().teamName()).isEqualTo("Team Beta");
        assertThat(response.costAgreement().costType()).isNull();
        assertThat(response.costAgreement().customItems()).isEmpty();
    }

    @Test
    void summaryRoundsDistanceHalfUpToTwoDigits() {
        Map<String, Object> row = new HashMap<>();
        row.put("matchId", matchId);
        row.put("type", "HAS_VENUE");
        row.put("effectiveStatus", "OPEN");
        row.put("skillLevels", "[]");
        row.put("matchStartTime", START);
        row.put("authorTeamId", authorTeamId);
        row.put("authorTeamReliabilityScore", 80);
        row.put("distanceKm", 1.245);

        MatchSummaryResponse summary = mapper.toSummary(projections.createProjection(MatchSummaryView.class, row));

        assertThat(summary.status()).isEqualTo("OPEN");
        assertThat(summary.startsAt()).isEqualTo(START);
        assertThat(summary.skillLevels()).isEmpty();
        assertThat(summary.distanceKm()).isEqualByComparingTo("1.25");
        assertThat(summary.venueOptionCount()).isNull();
    }
}
