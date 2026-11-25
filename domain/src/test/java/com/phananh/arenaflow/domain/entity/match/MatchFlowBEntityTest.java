package com.phananh.arenaflow.domain.entity.match;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchFlowBEntityTest {

    @Test
    void matchCarriesDepositAmountAndNoLongerHasSeparateConfirmedVenueAndTime() {
        Match match = Match.builder().costDepositAmount(300_000L).costDepositHolder("Trọng tài").build();

        assertThat(match.getCostDepositAmount()).isEqualTo(300_000L);
        assertThat(match.getCostDepositHolder()).isEqualTo("Trọng tài");
        assertThatThrownBy(() -> Match.class.getDeclaredField("confirmedVenueId"))
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> Match.class.getDeclaredField("confirmedMatchTime"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void costCustomItemBelongsToEitherMatchOrPairing() throws NoSuchFieldException {
        UUID pairingId = UUID.randomUUID();

        CostCustomItem item = CostCustomItem.builder().pairingId(pairingId).itemName("Trọng tài").amount(200_000L)
                .displayOrder(1).build();

        assertThat(item.getPairingId()).isEqualTo(pairingId);
        assertThat(item.getMatchId()).isNull();
        assertThat(column(CostCustomItem.class, "matchId").nullable()).isTrue();
        assertThat(column(CostCustomItem.class, "pairingId").name()).isEqualTo("pairing_id");
    }

    @Test
    void venueOptionKeepsInputOrder() throws NoSuchFieldException {
        MatchVenueOption option = MatchVenueOption.builder().displayOrder(2).build();

        assertThat(option.getDisplayOrder()).isEqualTo(2);
        Column column = column(MatchVenueOption.class, "displayOrder");
        assertThat(column.name()).isEqualTo("display_order");
        assertThat(column.nullable()).isFalse();
    }

    private static Column column(Class<?> type, String field) throws NoSuchFieldException {
        return type.getDeclaredField(field).getAnnotation(Column.class);
    }
}
