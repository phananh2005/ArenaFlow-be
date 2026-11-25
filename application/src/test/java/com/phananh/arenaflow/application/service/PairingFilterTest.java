package com.phananh.arenaflow.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PairingFilterTest {

    @Test
    void allMeansNoFilterOtherValuesPassThrough() {
        assertThat(PairingStatusFilter.ALL.toQueryValue()).isNull();
        assertThat(PairingStatusFilter.EXPIRED.toQueryValue()).isEqualTo("EXPIRED");
        assertThat(PairingStatusFilter.values()).extracting(Enum::name)
                .containsExactly("NEGOTIATING", "CONFIRMED", "SUPERSEDED", "EXPIRED", "ALL");
        assertThat(PairingRoleFilter.ALL.toQueryValue()).isNull();
        assertThat(PairingRoleFilter.AUTHOR.toQueryValue()).isEqualTo("AUTHOR");
        assertThat(PairingRoleFilter.INTERESTED.toQueryValue()).isEqualTo("INTERESTED");
    }
}
