package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.CostCustomItemResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.PairingDetailView;
import com.phananh.arenaflow.domain.projection.PairingSummaryView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PairingMapperTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 11, 10, 15, 0);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final PairingMapper mapper = Mappers.getMapper(PairingMapper.class);

    @Test
    void detailUsesEffectiveStatusCurrentCaptainsProposalAndOrderedItems() {
        Map<String, Object> values = detailValues();
        values.put("authorCaptainName", "Minh");
        values.put("authorCaptainDeleted", true);
        values.put("interestedCaptainName", null);
        values.put("interestedCaptainDeleted", false);
        values.put("proposedVenueId", UUID.randomUUID());
        values.put("proposedVenueName", "Sân Y");
        values.put("proposedVenueAddress", "10 Lê Lợi");
        values.put("proposedVenueStatus", "INACTIVE");
        values.put("proposedTimeOptionId", UUID.randomUUID());
        values.put("proposedStartTime", TIME.plusDays(2));
        values.put("proposedEndTime", TIME.plusDays(2).plusHours(2));
        values.put("finalConfirmedAt", TIME.plusHours(1));
        values.put("costType", "VENUE_ONLY");
        values.put("costDepositAmount", 200_000L);
        values.put("costDepositHolder", "Đội chủ nhà");
        CostCustomItem first = item("Trọng tài", 200_000L, 1);
        CostCustomItem second = item("Bóng", 50_000L, 2);

        PairingResponse response = mapper.toResponse(projection(PairingDetailView.class, values), List.of(first, second));

        assertThat(response.status()).isEqualTo("EXPIRED");
        assertThat(response.authorTeam().captainName()).isEqualTo("Người dùng đã xóa");
        assertThat(response.interestedTeam().captainName()).isNull();
        assertThat(response.interestedTeam().teamName()).isEqualTo("Beta");
        assertThat(response.proposal().venueName()).isEqualTo("Sân Y");
        assertThat(response.proposal().address()).isEqualTo("10 Lê Lợi");
        assertThat(response.proposal().venueStatus()).isEqualTo("INACTIVE");
        assertThat(response.proposal().startsAt()).isEqualTo(TIME.plusDays(2));
        assertThat(response.confirmedAt()).isEqualTo(TIME.plusHours(1));
        assertThat(response.costAgreement().costType()).isEqualTo("VENUE_ONLY");
        assertThat(response.costAgreement().depositAmount()).isEqualTo(200_000L);
        assertThat(response.costAgreement().depositHolder()).isEqualTo("Đội chủ nhà");
        assertThat(response.costAgreement().customItems()).extracting(CostCustomItemResponse::itemName)
                .containsExactly("Trọng tài", "Bóng");
        assertThat(response.costAgreement().customItems().get(0).itemId()).isEqualTo(first.getId());
        assertThat(response.conversationId()).isEqualTo(values.get("conversationId"));
        assertThat(response.matchExpiresAt()).isEqualTo(TIME.plusDays(1));
    }

    @Test
    void detailWithoutProposalHasNullProposalAndEmptyItems() {
        PairingResponse response = mapper.toResponse(projection(PairingDetailView.class, detailValues()), List.of());

        assertThat(response.proposal()).isNull();
        assertThat(response.costAgreement().costType()).isNull();
        assertThat(response.costAgreement().customItems()).isEmpty();
    }

    @Test
    void createdResponseBuildsFromLoadedDataWithoutRereading() {
        UUID matchId = UUID.randomUUID();
        UUID authorTeamId = UUID.randomUUID();
        UUID interestedTeamId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Match match = Match.builder().id(matchId).authorTeamId(authorTeamId).expiresAt(TIME.plusDays(1)).build();
        Pairing pairing = Pairing.builder().id(UUID.randomUUID()).matchId(matchId).interestedTeamId(interestedTeamId)
                .status(Pairing.Status.NEGOTIATING).build();
        TeamCaptainContextView caller = projection(TeamCaptainContextView.class, Map.of("status", "ACTIVE",
                "captain", true, "name", "Beta", "reliabilityScore", 75, "userDisplayName", "Nam", "userDeleted", false));
        Map<String, Object> availabilityValues = new HashMap<>(Map.of("authorTeamStatus", "ACTIVE",
                "sportActive", true, "authorTeamName", "Alpha", "authorTeamReliabilityScore", 80,
                "authorCaptainName", "Minh", "authorCaptainDeleted", false));
        availabilityValues.put("authorTeamAvatarUrl", "https://img/alpha.png");
        MatchAvailabilityView availability = projection(MatchAvailabilityView.class, availabilityValues);

        PairingResponse response = mapper.toCreatedResponse(pairing, match, caller, availability, conversationId);

        assertThat(response.pairingId()).isEqualTo(pairing.getId());
        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.conversationId()).isEqualTo(conversationId);
        assertThat(response.status()).isEqualTo("NEGOTIATING");
        assertThat(response.revision()).isZero();
        assertThat(response.matchExpiresAt()).isEqualTo(TIME.plusDays(1));
        assertThat(response.authorTeam().teamId()).isEqualTo(authorTeamId);
        assertThat(response.authorTeam().teamName()).isEqualTo("Alpha");
        assertThat(response.authorTeam().avatarUrl()).isEqualTo("https://img/alpha.png");
        assertThat(response.authorTeam().captainName()).isEqualTo("Minh");
        assertThat(response.interestedTeam().teamId()).isEqualTo(interestedTeamId);
        assertThat(response.interestedTeam().reliabilityScore()).isEqualTo(75);
        assertThat(response.interestedTeam().captainName()).isEqualTo("Nam");
        assertThat(response.proposal()).isNull();
        assertThat(response.costAgreement().customItems()).isEmpty();
        assertThat(response.costAgreement().depositAmount()).isNull();
    }

    @Test
    void summaryRenamesProposalConfirmationAndMatchStart() {
        UUID venueId = UUID.randomUUID();
        Map<String, Object> values = new HashMap<>();
        values.put("pairingId", UUID.randomUUID());
        values.put("effectiveStatus", "CONFIRMED");
        values.put("revision", 3);
        values.put("proposedVenueId", venueId);
        values.put("finalConfirmedAt", TIME);
        values.put("matchStartTime", TIME.plusDays(2));
        values.put("interestedTeamReliabilityScore", 70);

        PairingSummaryResponse response = mapper.toSummary(projection(PairingSummaryView.class, values));

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.revision()).isEqualTo(3);
        assertThat(response.proposalVenueId()).isEqualTo(venueId);
        assertThat(response.confirmedAt()).isEqualTo(TIME);
        assertThat(response.matchStartsAt()).isEqualTo(TIME.plusDays(2));
        assertThat(response.interestedTeamReliabilityScore()).isEqualTo(70);
    }

    private static Map<String, Object> detailValues() {
        Map<String, Object> values = new HashMap<>();
        values.put("pairingId", UUID.randomUUID());
        values.put("matchId", UUID.randomUUID());
        values.put("conversationId", UUID.randomUUID());
        values.put("effectiveStatus", "EXPIRED");
        values.put("revision", 2);
        values.put("matchExpiresAt", TIME.plusDays(1));
        values.put("authorTeamId", UUID.randomUUID());
        values.put("authorTeamName", "Alpha");
        values.put("authorTeamReliabilityScore", 80);
        values.put("interestedTeamId", UUID.randomUUID());
        values.put("interestedTeamName", "Beta");
        values.put("interestedTeamReliabilityScore", 75);
        values.put("createdAt", TIME);
        values.put("updatedAt", TIME);
        return values;
    }

    private static CostCustomItem item(String name, long amount, int order) {
        return CostCustomItem.builder().id(UUID.randomUUID()).itemName(name).amount(amount).displayOrder(order).build();
    }

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }
}
