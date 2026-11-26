package com.phananh.arenaflow.application.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReliabilityReasonTest {

    @Test
    void includesCompletionRatingAndReportReasons() {
        assertThat(ReliabilityReason.values()).contains(ReliabilityReason.MATCH_COMPLETED,
                ReliabilityReason.FIVE_STAR_RATING, ReliabilityReason.REPORT_RECEIVED,
                ReliabilityReason.REPORT_REJECTED_REFUND);
    }
}
