package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.domain.entity.rating.Report;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPenaltyPolicyTest {

    @Test
    void tiersFollowPenaltyTablePerReportType() {
        assertThat(ReportPenaltyPolicy.tiers(Report.ReportType.NOSHOW)).containsExactly(5, 8, 12, 15);
        assertThat(ReportPenaltyPolicy.tiers(Report.ReportType.BAD_PLAY)).containsExactly(3, 5, 8, 10);
        assertThat(ReportPenaltyPolicy.tiers(Report.ReportType.BAD_COMMUNICATION)).containsExactly(2, 4, 6, 8);
        assertThat(ReportPenaltyPolicy.tiers(Report.ReportType.SKILL_MISMATCH)).containsExactly(1, 2, 3, 4);
    }

    @Test
    void returnsCopySoCallersCannotChangeTheTable() {
        ReportPenaltyPolicy.tiers(Report.ReportType.NOSHOW)[0] = 99;

        assertThat(ReportPenaltyPolicy.tiers(Report.ReportType.NOSHOW)[0]).isEqualTo(5);
    }
}
