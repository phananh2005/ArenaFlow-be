package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateDisputeRequest;
import com.phananh.arenaflow.application.dto.response.DisputeCreatedResponse;
import com.phananh.arenaflow.application.event.reputation.ReportDisputedEvent;
import com.phananh.arenaflow.application.mapper.ReputationMapper;
import com.phananh.arenaflow.application.service.impl.DisputeServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Dispute;
import com.phananh.arenaflow.domain.projection.DisputeContextView;
import com.phananh.arenaflow.domain.repository.DisputeRepository;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisputeServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T15:00:00Z"), ZoneId.systemDefault());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 22, 0);

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final DisputeRepository disputeRepository = mock(DisputeRepository.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID reportId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID reporter = UUID.randomUUID();
    private final UUID reported = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();

    private DisputeService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new DisputeServiceImpl(reportRepository, disputeRepository, retrier,
                Mappers.getMapper(ReputationMapper.class), publisher, CLOCK);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(disputeRepository.save(any())).thenAnswer(invocation -> {
            Dispute dispute = invocation.getArgument(0);
            ReflectionTestUtils.setField(dispute, "id", disputeId);
            ReflectionTestUtils.setField(dispute, "createdAt", NOW);
            return dispute;
        });
    }

    @Test
    void guardOrder() {
        when(reportRepository.findDisputeContext(reportId, callerId)).thenReturn(Optional.empty());
        assertCode(ErrorCode.REPORT_001);

        stub(context("DISBANDED", false, "NOSHOW", "AUTO_ACCEPTED", false, NOW.minusHours(1)));
        assertCode(ErrorCode.TEAM_016);

        stub(context("INACTIVE", false, "NOSHOW", "AUTO_ACCEPTED", false, NOW.minusHours(1)));
        assertCode(ErrorCode.TEAM_015);

        stub(context("ACTIVE", false, "NOSHOW", "AUTO_ACCEPTED", false, NOW.minusHours(1)));
        assertCode(ErrorCode.TEAM_002);

        stub(context("ACTIVE", true, "BAD_PLAY", "AUTO_ACCEPTED", false, NOW.minusHours(1)));
        assertCode(ErrorCode.REPORT_006);

        stub(context("ACTIVE", true, "NOSHOW", "DISPUTED", false, NOW.minusHours(1)));
        assertCode(ErrorCode.REPORT_008);

        stub(context("ACTIVE", true, "NOSHOW", "AUTO_ACCEPTED", true, NOW.minusHours(1)));
        assertCode(ErrorCode.REPORT_008);

        stub(context("ACTIVE", true, "NOSHOW", "AUTO_ACCEPTED", false, NOW.minusHours(24)));
        assertCode(ErrorCode.REPORT_007);
        verify(reportRepository, never()).markDisputed(any(), any(), any());
    }

    @Test
    void conditionalUpdateMissIsRetriedAsConflict() {
        stub(context("ACTIVE", true, "NOSHOW", "AUTO_ACCEPTED", false, NOW.minusHours(1)));
        when(reportRepository.markDisputed(reportId, NOW, NOW.minusHours(24))).thenReturn(0);

        assertThatThrownBy(() -> service.createDispute(reportId, callerId, new CreateDisputeRequest("Có mặt")))
                .isInstanceOf(ConcurrencyConflictException.class);
        verify(disputeRepository, never()).save(any());
    }

    @Test
    void createsPendingDisputeAndPublishesEvent() {
        stub(context("ACTIVE", true, "NOSHOW", "AUTO_ACCEPTED", false, NOW.minusHours(23)));
        when(reportRepository.markDisputed(reportId, NOW, NOW.minusHours(24))).thenReturn(1);

        DisputeCreatedResponse response = service.createDispute(reportId, callerId, new CreateDisputeRequest("Có mặt"));

        assertThat(response.disputeId()).isEqualTo(disputeId);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.reportStatus()).isEqualTo("DISPUTED");
        assertThat(response.evidence()).isEqualTo("Có mặt");
        assertThat(response.createdAt()).isEqualTo(NOW);
        verify(publisher).publishEvent(new ReportDisputedEvent(disputeId, reportId, reporter, reported, NOW));
    }

    private void stub(DisputeContextView view) {
        when(reportRepository.findDisputeContext(reportId, callerId)).thenReturn(Optional.of(view));
    }

    private DisputeContextView context(String reportedStatus, boolean callerCaptain, String type, String status,
                                       boolean disputeExists, LocalDateTime createdAt) {
        Map<String, Object> values = new HashMap<>();
        values.put("reportId", reportId);
        values.put("reportType", type);
        values.put("status", status);
        values.put("createdAt", createdAt);
        values.put("reporterTeamId", reporter);
        values.put("reportedTeamId", reported);
        values.put("reportedTeamStatus", reportedStatus);
        values.put("callerReportedCaptain", callerCaptain);
        values.put("disputeExists", disputeExists);
        return projections.createProjection(DisputeContextView.class, values);
    }

    private void assertCode(ErrorCode code) {
        assertThatThrownBy(() -> service.createDispute(reportId, callerId, new CreateDisputeRequest("Có mặt")))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode()).isEqualTo(code);
    }
}
