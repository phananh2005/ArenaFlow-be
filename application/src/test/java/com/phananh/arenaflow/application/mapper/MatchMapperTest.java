package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.CostCustomItemResponse;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.dto.response.MatchSummaryResponse;
import com.phananh.arenaflow.application.dto.response.MatchTimeOptionResponse;
import com.phananh.arenaflow.application.dto.response.MatchVenueResponse;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchOptionRowView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.projection.TimeOptionView;
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

/** Dựng response kèo chi tiết / rút gọn / kèo luồng B vừa tạo từ projection và dữ liệu đã nạp. */
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

    private MatchOptionRowView optionRow(String kind, Integer displayOrder, UUID venueId, UUID timeOptionId,
                                         LocalDateTime startsAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("kind", kind);
        row.put("displayOrder", displayOrder);
        row.put("venueId", venueId);
        row.put("venueName", venueId == null ? null : "Sân " + displayOrder);
        row.put("address", venueId == null ? null : "Địa chỉ " + displayOrder);
        row.put("latitude", venueId == null ? null : new BigDecimal("10.77"));
        row.put("longitude", venueId == null ? null : new BigDecimal("106.70"));
        row.put("venueStatus", venueId == null ? null : "INACTIVE");
        row.put("primaryImageUrl", null);
        row.put("timeOptionId", timeOptionId);
        row.put("startsAt", startsAt);
        row.put("endsAt", startsAt == null ? null : startsAt.plusHours(2));
        return projections.createProjection(MatchOptionRowView.class, row);
    }

    @Test
    void detailOfNeedVenueSplitsOptionRowsByKindKeepingOrder() {
        Map<String, Object> row = detailRow();
        row.put("type", "NEED_VENUE");
        row.remove("venueId");
        UUID pairingId = UUID.randomUUID();
        row.put("confirmedPairingId", pairingId);
        row.put("costDepositAmount", 200_000L);
        row.put("costDepositHolder", "Chủ nhà");
        UUID venueY = UUID.randomUUID();
        UUID venueZ = UUID.randomUUID();
        UUID early = UUID.randomUUID();
        UUID late = UUID.randomUUID();

        MatchResponse response = mapper.toResponse(projections.createProjection(MatchDetailView.class, row), List.of(),
                List.of(optionRow("TIME", null, null, early, START), optionRow("TIME", null, null, late, START.plusHours(1)),
                        optionRow("VENUE", 1, venueY, null, null), optionRow("VENUE", 2, venueZ, null, null)));

        assertThat(response.venue()).isNull();
        assertThat(response.venueOptions()).containsExactly(
                new MatchVenueResponse(venueY, "Sân 1", "Địa chỉ 1", new BigDecimal("10.77"), new BigDecimal("106.70"),
                        "INACTIVE", null),
                new MatchVenueResponse(venueZ, "Sân 2", "Địa chỉ 2", new BigDecimal("10.77"), new BigDecimal("106.70"),
                        "INACTIVE", null));
        assertThat(response.timeOptions()).containsExactly(
                new MatchTimeOptionResponse(early, START, START.plusHours(2)),
                new MatchTimeOptionResponse(late, START.plusHours(1), START.plusHours(3)));
        assertThat(response.confirmedPairingId()).isEqualTo(pairingId);
        assertThat(response.costAgreement().depositAmount()).isEqualTo(200_000L);
        assertThat(response.costAgreement().depositHolder()).isEqualTo("Chủ nhà");
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

    @Test
    void createdNeedVenueBuildsFromLoadedDataWithTimeOptionsByStart() {
        UUID sportId = UUID.randomUUID();
        Match match = Match.builder().id(matchId).type(Match.Type.NEED_VENUE).status(Match.Status.OPEN)
                .authorTeamId(authorTeamId).sportId(sportId).format(Match.Format.SEVEN_A_SIDE)
                .skillLevels(List.of("Nhiệt tình")).ageNote("U23").matchStartTime(START).matchEndTime(START.plusHours(2))
                .expiresAt(START.minusHours(6)).createdAt(START.minusDays(3)).updatedAt(START.minusDays(3)).build();
        Map<String, Object> author = new HashMap<>();
        author.put("name", "Team Alpha");
        author.put("avatarUrl", "https://img/alpha.png");
        author.put("reliabilityScore", 80);
        UUID venueY = UUID.randomUUID();
        UUID venueZ = UUID.randomUUID();
        UUID early = UUID.randomUUID();
        UUID late = UUID.randomUUID();

        MatchResponse response = mapper.toCreatedNeedVenue(match,
                projections.createProjection(TeamCaptainContextView.class, author),
                Sport.builder().id(sportId).name("Bóng đá").build(),
                List.of(selectable(venueY, "Sân Y"), selectable(venueZ, "Sân Z")),
                List.of(timeOption(late, START.plusHours(1)), timeOption(early, START)));

        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.type()).isEqualTo("NEED_VENUE");
        assertThat(response.status()).isEqualTo("OPEN");
        assertThat(response.sportId()).isEqualTo(sportId);
        assertThat(response.sportName()).isEqualTo("Bóng đá");
        assertThat(response.format()).isEqualTo("SEVEN_A_SIDE");
        assertThat(response.skillLevels()).containsExactly("Nhiệt tình");
        assertThat(response.ageNote()).isEqualTo("U23");
        assertThat(response.startsAt()).isEqualTo(START);
        assertThat(response.endsAt()).isEqualTo(START.plusHours(2));
        assertThat(response.expiresAt()).isEqualTo(START.minusHours(6));
        assertThat(response.authorTeam().teamId()).isEqualTo(authorTeamId);
        assertThat(response.authorTeam().teamName()).isEqualTo("Team Alpha");
        assertThat(response.authorTeam().avatarUrl()).isEqualTo("https://img/alpha.png");
        assertThat(response.authorTeam().reliabilityScore()).isEqualTo(80);
        assertThat(response.opponentTeam()).isNull();
        assertThat(response.venue()).isNull();
        assertThat(response.venueOptions()).extracting(MatchVenueResponse::venueId).containsExactly(venueY, venueZ);
        assertThat(response.venueOptions().get(0)).isEqualTo(new MatchVenueResponse(venueY, "Sân Y", "Địa chỉ Sân Y",
                new BigDecimal("10.77"), new BigDecimal("106.70"), "ACTIVE", "https://img/Sân Y.png"));
        assertThat(response.timeOptions()).containsExactly(
                new MatchTimeOptionResponse(early, START, START.plusHours(2)),
                new MatchTimeOptionResponse(late, START.plusHours(1), START.plusHours(3)));
        assertThat(response.confirmedPairingId()).isNull();
        assertThat(response.costAgreement().costType()).isNull();
        assertThat(response.costAgreement().depositAmount()).isNull();
        assertThat(response.costAgreement().customItems()).isEmpty();
        assertThat(response.cancelledAt()).isNull();
        assertThat(response.createdAt()).isEqualTo(START.minusDays(3));
        assertThat(response.updatedAt()).isEqualTo(START.minusDays(3));
    }

    private SelectableVenueView selectable(UUID id, String name) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("name", name);
        row.put("address", "Địa chỉ " + name);
        row.put("latitude", new BigDecimal("10.77"));
        row.put("longitude", new BigDecimal("106.70"));
        row.put("status", "ACTIVE");
        row.put("primaryImageUrl", "https://img/" + name + ".png");
        return projections.createProjection(SelectableVenueView.class, row);
    }

    private TimeOptionView timeOption(UUID id, LocalDateTime startsAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("proposedStartTime", startsAt);
        row.put("proposedEndTime", startsAt.plusHours(2));
        return projections.createProjection(TimeOptionView.class, row);
    }
}
