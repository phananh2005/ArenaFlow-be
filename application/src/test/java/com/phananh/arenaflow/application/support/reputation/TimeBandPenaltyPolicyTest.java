package com.phananh.arenaflow.application.support.reputation;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeBandPenaltyPolicyTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 22, 18, 0);
    private final TimeBandPenaltyPolicy policy = TimeBandPenaltyPolicy.MATCH_CANCELLATION;

    @Test
    void exactlyTwentyFourHoursBeforeIsLightestBand() {
        assertThat(policy.penaltyFor(START, START.minusHours(24))).isEqualTo(-2);
        assertThat(policy.penaltyFor(START, START.minusDays(3))).isEqualTo(-2);
    }

    @Test
    void justUnderTwentyFourHoursDownToExactlyFourHoursIsMiddleBand() {
        assertThat(policy.penaltyFor(START, START.minusHours(24).plusSeconds(1))).isEqualTo(-5);
        assertThat(policy.penaltyFor(START, START.minusHours(4))).isEqualTo(-5);
    }

    @Test
    void underFourHoursIsHeaviestBand() {
        assertThat(policy.penaltyFor(START, START.minusHours(4).plusSeconds(1))).isEqualTo(-10);
        assertThat(policy.penaltyFor(START, START.minusSeconds(1))).isEqualTo(-10);
    }
}
