package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.ReportResponse;
import com.phananh.arenaflow.application.service.ReportDirection;
import com.phananh.arenaflow.domain.projection.TeamReportView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReputationMapperTest {

    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 11, 12, 21, 10);

    private final ReputationMapper mapper = Mappers.getMapper(ReputationMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();

    @Test
    void receivedReportShowsPenaltyEvidenceAndDisputeRightBeforeDeadline() {
        ReportResponse response = mapper.toReport(report("AUTO_ACCEPTED", true), ReportDirection.RECEIVED,
                CREATED.plusHours(1));

        assertThat(response.direction()).isEqualTo("RECEIVED");
        assertThat(response.reliabilityPenalty()).isEqualTo(5);
        assertThat(response.canDispute()).isTrue();
        assertThat(response.disputeDeadlineAt()).isEqualTo(CREATED.plusHours(24));
        assertThat(response.dispute().evidence()).isEqualTo("Có mặt");
        assertThat(response.counterpartTeam().name()).isEqualTo("FC Hoa Lư");
    }

    @Test
    void sentReportHidesPenaltyAndEvidence() {
        ReportResponse response = mapper.toReport(report("DISPUTED", true), ReportDirection.SENT, CREATED.plusHours(1));

        assertThat(response.reliabilityPenalty()).isNull();
        assertThat(response.dispute().evidence()).isNull();
        assertThat(response.dispute().status()).isEqualTo("PENDING");
        assertThat(response.canDispute()).isFalse();
    }

    @Test
    void undisputedNoShowAfterDeadlineIsConfirmed() {
        ReportResponse response = mapper.toReport(report("AUTO_ACCEPTED", false), ReportDirection.RECEIVED,
                CREATED.plusHours(24));

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.canDispute()).isFalse();
        assertThat(response.dispute()).isNull();
    }

    private TeamReportView report(String status, boolean withDispute) {
        Map<String, Object> values = new HashMap<>();
        values.put("reportId", UUID.randomUUID());
        values.put("matchId", UUID.randomUUID());
        values.put("matchStartTime", CREATED.minusHours(3));
        values.put("counterpartTeamId", UUID.randomUUID());
        values.put("counterpartTeamName", "FC Hoa Lư");
        values.put("reportType", "NOSHOW");
        values.put("reason", "Không đến");
        values.put("status", status);
        values.put("reliabilityPenalty", 5);
        values.put("createdAt", CREATED);
        if (withDispute) {
            values.put("disputeId", UUID.randomUUID());
            values.put("disputeStatus", "PENDING");
            values.put("disputeEvidence", "Có mặt");
            values.put("disputeCreatedAt", CREATED.plusHours(1));
        }
        return projections.createProjection(TeamReportView.class, values);
    }
}
