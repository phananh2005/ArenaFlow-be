package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.application.service.ReportDirection;
import com.phananh.arenaflow.domain.entity.rating.Report;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.phananh.arenaflow.domain.entity.rating.Report.ReportType.BAD_PLAY;
import static com.phananh.arenaflow.domain.entity.rating.Report.ReportType.NOSHOW;
import static com.phananh.arenaflow.domain.entity.rating.Report.Status.AUTO_ACCEPTED;
import static com.phananh.arenaflow.domain.entity.rating.Report.Status.CONFIRMED;
import static com.phananh.arenaflow.domain.entity.rating.Report.Status.DISPUTED;
import static org.assertj.core.api.Assertions.assertThat;

class ReportStatusResolverTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 12, 21, 10);
    private static final LocalDateTime DEADLINE = CREATED.plusHours(24);

    @Test
    void noShowAutoAcceptedBecomesConfirmedAtDeadline() {
        assertThat(ReportStatusResolver.effectiveStatus(AUTO_ACCEPTED, NOSHOW, CREATED, DEADLINE.minusNanos(1000)))
                .isEqualTo(AUTO_ACCEPTED);
        assertThat(ReportStatusResolver.effectiveStatus(AUTO_ACCEPTED, NOSHOW, CREATED, DEADLINE)).isEqualTo(CONFIRMED);
    }

    @Test
    void otherTypesAndDisputedReportsKeepStoredStatus() {
        assertThat(ReportStatusResolver.effectiveStatus(AUTO_ACCEPTED, BAD_PLAY, CREATED, DEADLINE.plusDays(3)))
                .isEqualTo(AUTO_ACCEPTED);
        assertThat(ReportStatusResolver.effectiveStatus(DISPUTED, NOSHOW, CREATED, DEADLINE.plusDays(3)))
                .isEqualTo(DISPUTED);
    }

    @Test
    void deadlineOnlyForNoShow() {
        assertThat(ReportStatusResolver.disputeDeadline(NOSHOW, CREATED)).isEqualTo(DEADLINE);
        assertThat(ReportStatusResolver.disputeDeadline(Report.ReportType.BAD_COMMUNICATION, CREATED)).isNull();
    }

    @Test
    void canDisputeOnlyReceivedNoShowAutoAcceptedBeforeDeadline() {
        LocalDateTime before = DEADLINE.minusMinutes(1);

        assertThat(ReportStatusResolver.canDispute(ReportDirection.RECEIVED, NOSHOW, AUTO_ACCEPTED, CREATED, before)).isTrue();
        assertThat(ReportStatusResolver.canDispute(ReportDirection.SENT, NOSHOW, AUTO_ACCEPTED, CREATED, before)).isFalse();
        assertThat(ReportStatusResolver.canDispute(ReportDirection.RECEIVED, BAD_PLAY, AUTO_ACCEPTED, CREATED, before)).isFalse();
        assertThat(ReportStatusResolver.canDispute(ReportDirection.RECEIVED, NOSHOW, DISPUTED, CREATED, before)).isFalse();
        assertThat(ReportStatusResolver.canDispute(ReportDirection.RECEIVED, NOSHOW, AUTO_ACCEPTED, CREATED, DEADLINE)).isFalse();
    }
}
