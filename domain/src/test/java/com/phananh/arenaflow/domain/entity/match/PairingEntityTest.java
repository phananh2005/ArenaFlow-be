package com.phananh.arenaflow.domain.entity.match;

import com.phananh.arenaflow.common.time.AppTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PairingEntityTest {

    @Test
    void newPairingStartsAtRevisionZeroWithoutProposalOrCost() {
        Pairing pairing = Pairing.builder()
                .matchId(UUID.randomUUID())
                .interestedTeamId(UUID.randomUUID())
                .status(Pairing.Status.NEGOTIATING)
                .build();

        assertThat(pairing.getRevision()).isZero();
        assertThat(pairing.getProposedVenueId()).isNull();
        assertThat(pairing.getProposedTimeOptionId()).isNull();
        assertThat(pairing.getCostType()).isNull();
        assertThat(pairing.getCostDepositAmount()).isNull();
        assertThat(pairing.getCostDepositHolder()).isNull();
    }

    @Test
    void carriesProposalCostAndDepositOfTheNegotiationPackage() {
        UUID venueId = UUID.randomUUID();
        UUID timeOptionId = UUID.randomUUID();

        Pairing pairing = Pairing.builder()
                .proposedVenueId(venueId)
                .proposedTimeOptionId(timeOptionId)
                .costType(Match.CostType.BOTH)
                .costSplit(Match.CostSplit.FIFTY_FIFTY)
                .costVenueAmount(500_000L)
                .costDrinksAmount(100_000L)
                .costDepositAmount(200_000L)
                .costDepositHolder("Đội chủ nhà")
                .build();

        assertThat(pairing.getProposedVenueId()).isEqualTo(venueId);
        assertThat(pairing.getProposedTimeOptionId()).isEqualTo(timeOptionId);
        assertThat(pairing.getCostType()).isEqualTo(Match.CostType.BOTH);
        assertThat(pairing.getCostSplit()).isEqualTo(Match.CostSplit.FIFTY_FIFTY);
        assertThat(pairing.getCostVenueAmount()).isEqualTo(500_000L);
        assertThat(pairing.getCostDrinksAmount()).isEqualTo(100_000L);
        assertThat(pairing.getCostDepositAmount()).isEqualTo(200_000L);
        assertThat(pairing.getCostDepositHolder()).isEqualTo("Đội chủ nhà");
    }

    @Test
    void persistCallbackSetsCreatedAndUpdatedAtToVietnamNow() {
        Pairing pairing = Pairing.builder().status(Pairing.Status.NEGOTIATING).build();
        LocalDateTime before = AppTime.now();

        pairing.onCreate();

        assertThat(pairing.getCreatedAt()).isBetween(before, AppTime.now());
        assertThat(pairing.getUpdatedAt()).isEqualTo(pairing.getCreatedAt());
    }

    @Test
    void updateCallbackRefreshesUpdatedAtOnly() {
        Pairing pairing = Pairing.builder().status(Pairing.Status.NEGOTIATING).build();
        pairing.onCreate();
        LocalDateTime createdAt = pairing.getCreatedAt();

        pairing.onUpdate();

        assertThat(pairing.getCreatedAt()).isEqualTo(createdAt);
        assertThat(pairing.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }
}
