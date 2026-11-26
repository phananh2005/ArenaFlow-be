package com.phananh.arenaflow.application.support.reputation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ReputationRulesTest {

    @Test
    void reliabilityLevelBoundaries() {
        assertThat(ReputationRules.reliabilityLevel(100)).isEqualTo(ReputationRules.ReliabilityLevel.EXCELLENT);
        assertThat(ReputationRules.reliabilityLevel(95)).isEqualTo(ReputationRules.ReliabilityLevel.EXCELLENT);
        assertThat(ReputationRules.reliabilityLevel(94)).isEqualTo(ReputationRules.ReliabilityLevel.NORMAL);
        assertThat(ReputationRules.reliabilityLevel(70)).isEqualTo(ReputationRules.ReliabilityLevel.NORMAL);
        assertThat(ReputationRules.reliabilityLevel(69)).isEqualTo(ReputationRules.ReliabilityLevel.WARNING);
        assertThat(ReputationRules.reliabilityLevel(50)).isEqualTo(ReputationRules.ReliabilityLevel.WARNING);
        assertThat(ReputationRules.reliabilityLevel(49)).isEqualTo(ReputationRules.ReliabilityLevel.CRITICAL);
        assertThat(ReputationRules.reliabilityLevel(0)).isEqualTo(ReputationRules.ReliabilityLevel.CRITICAL);
    }

    @Test
    void constantsMatchBusinessRules() {
        assertThat(ReputationRules.COMPLETION_POINTS).isEqualTo(2);
        assertThat(ReputationRules.STREAK_MILESTONE).isEqualTo(5);
        assertThat(ReputationRules.STREAK_BONUS_5).isEqualTo(5);
        assertThat(ReputationRules.STREAK_BONUS_10).isEqualTo(10);
        assertThat(ReputationRules.FIVE_STAR_BONUS).isEqualTo(1);
        assertThat(ReputationRules.DISPUTE_WINDOW).isEqualTo(Duration.ofHours(24));
        assertThat(ReputationRules.LATE_CANCEL_WINDOW_HOURS).isEqualTo(24);
        assertThat(ReputationRules.NEWCOMER_THRESHOLD).isEqualTo(5);
        assertThat(ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE).isEqualTo(5);
        assertThat(ReputationRules.BADGE_MIN_AVG_EXCLUSIVE).isEqualTo(3.0);
        assertThat(ReputationRules.BADGE_MIN_SCORE).isEqualTo(70);
    }
}
