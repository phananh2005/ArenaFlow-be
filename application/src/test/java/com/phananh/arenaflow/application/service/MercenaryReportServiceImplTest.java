package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateReportRequest;
import com.phananh.arenaflow.application.dto.response.MercenaryReportResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryReportCreatedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryReputationMapper;
import com.phananh.arenaflow.application.service.impl.MercenaryReportServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.application.support.reputation.VerificationRefresher;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.projection.MercenaryReportContextView;
import com.phananh.arenaflow.domain.projection.MercenaryReportView;
import com.phananh.arenaflow.domain.repository.ReportRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

class MercenaryReportServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final VerificationRefresher verificationRefresher = mock(VerificationRefresher.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID applicantId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private MercenaryReportServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new MercenaryReportServiceImpl(reportRepository, teamGuard, new MercenaryGuard(), reputationService,
                verificationRefresher, retrier, Mappers.getMapper(MercenaryReputationMapper.class), eventPublisher,
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(reportRepository.saveAndFlush(any(Report.class))).thenAnswer(invocation -> {
            Report report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", reportId);
            ReflectionTestUtils.setField(report, "createdAt", NOW);
            return report;
        });
    }

    @Test
    void guardOrderForCallerSide() {
        assertCreateError(Map.of("callerDeleted", true), "NOSHOW", ErrorCode.USER_001);
        assertCreateError(Map.of("applicationId", ""), "NOSHOW", ErrorCode.MERCENARY_002);
        assertCreateError(Map.of("callerApplicant", true), "NOSHOW", ErrorCode.MERCENARY_019);
        assertCreateError(Map.of("callerCaptain", false, "teamStatus", "DISBANDED", "callerDisbandedBy", true),
                "BAD_COMMUNICATION", ErrorCode.TEAM_016);
        assertCreateError(Map.of("callerCaptain", false), "NOSHOW", ErrorCode.MERCENARY_014);
        assertCreateError(Map.of("teamStatus", "INACTIVE"), "NOSHOW", ErrorCode.TEAM_015);
        assertCreateError(Map.of("teamReported", true), "NOSHOW", ErrorCode.MERCENARY_022);
        verify(reportRepository, never()).saveAndFlush(any());
    }

    /** Bảng kiểm ngữ cảnh report: trận kết thúc + đơn APPROVED → 4 loại; có tin nhắn → chỉ giao tiếp tệ; còn lại → không. */
    @ParameterizedTest
    @CsvSource({
            "true, APPROVED, false, NOSHOW, ",
            "true, REJECTED, true, BAD_COMMUNICATION, ",
            "true, REJECTED, true, NOSHOW, MERCENARY_021",
            "false, APPROVED, true, BAD_COMMUNICATION, ",
            "false, APPROVED, true, BAD_PLAY, MERCENARY_021",
            "false, APPROVED, false, BAD_COMMUNICATION, MERCENARY_020",
            "true, WITHDRAWN, false, BAD_COMMUNICATION, MERCENARY_020"})
    void reportContextTable(boolean ended, String status, boolean hasMessages, String type, String error) {
        when(reportRepository.findMercenaryReportContext(applicationId, callerId, type, NOW)).thenReturn(Optional.of(
                ctx(Map.of("gameEnded", ended, "applicationStatus", status, "hasMessages", hasMessages))));
        when(reputationService.applyUserReportPenalty(any(), any(), any())).thenReturn(2);
        CreateReportRequest request = new CreateReportRequest(type, "Lý do");

        if (error == null) {
            MercenaryReportResponse response = service.createReport(applicationId, callerId, request);
            assertThat(response.reportType()).isEqualTo(type);
        } else {
            assertError(() -> service.createReport(applicationId, callerId, request), ErrorCode.valueOf(error));
        }
    }

    @Test
    void captainReportPenalisesMercenaryAndStoresPoints() {
        when(reportRepository.findMercenaryReportContext(applicationId, callerId, "NOSHOW", NOW))
                .thenReturn(Optional.of(ctx(Map.of())));
        when(reputationService.applyUserReportPenalty(applicantId, Report.ReportType.NOSHOW, reportId)).thenReturn(5);

        MercenaryReportResponse response = service.createReport(applicationId, callerId,
                new CreateReportRequest("NOSHOW", "Không đến"));

        ArgumentCaptor<Report> saved = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getReporterType()).isEqualTo(Report.ReporterType.TEAM);
        assertThat(saved.getValue().getReportedType()).isEqualTo(Report.ReportedType.INDIVIDUAL);
        assertThat(saved.getValue().getReportedId()).isEqualTo(applicantId);
        assertThat(saved.getValue().getReliabilityPenalty()).isEqualTo(5);
        verify(verificationRefresher).refreshUsersAfterCommit(Set.of(applicantId));
        assertThat(response.direction()).isEqualTo("SENT");
        assertThat(response.reporterSide()).isEqualTo("TEAM");
        assertThat(response.reliabilityPenalty()).isNull();
        assertThat(response.disputeDeadlineAt()).isEqualTo(NOW.plusHours(24));
        assertThat(response.canDispute()).isFalse();
        verify(eventPublisher).publishEvent(any(MercenaryReportCreatedEvent.class));
    }

    @Test
    void applicantReportPenalisesTeam() {
        when(reportRepository.findMercenaryReportContext(applicationId, callerId, "BAD_PLAY", NOW))
                .thenReturn(Optional.of(ctx(Map.of("callerCaptain", false, "callerApplicant", true))));
        when(reputationService.applyReportPenalty(teamId, Report.ReportType.BAD_PLAY, reportId)).thenReturn(3);

        MercenaryReportResponse response = service.createReport(applicationId, callerId,
                new CreateReportRequest("BAD_PLAY", "Chơi xấu"));

        assertThat(response.reporterSide()).isEqualTo("MERCENARY");
        verify(verificationRefresher).refreshTeamsAfterCommit(Set.of(teamId));
        verify(reputationService, never()).applyUserReportPenalty(any(), any(), any());
    }

    @Test
    void duplicateRaceMapsToMercenary022() {
        when(reportRepository.findMercenaryReportContext(applicationId, callerId, "NOSHOW", NOW))
                .thenReturn(Optional.of(ctx(Map.of())));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(DataIntegrityViolationFixtures.violation("idx_report_external_unique"));

        assertError(() -> service.createReport(applicationId, callerId, new CreateReportRequest("NOSHOW", "x")),
                ErrorCode.MERCENARY_022);
    }

    @Test
    void receivedNoshowWithinWindowCanBeDisputedAndShowsPenalty() {
        Map<String, Object> row = new HashMap<>();
        row.put("reportId", reportId);
        row.put("applicationId", applicationId);
        row.put("reporterType", "TEAM");
        row.put("reportType", "NOSHOW");
        row.put("reason", "Không đến");
        row.put("status", "AUTO_ACCEPTED");
        row.put("reliabilityPenalty", 5);
        row.put("createdAt", NOW.minusHours(2));
        row.put("teamId", teamId);
        row.put("mercenaryId", applicantId);
        row.put("mercenaryName", "M");
        row.put("mercenaryDeleted", true);
        when(reportRepository.findMercenaryReports("INDIVIDUAL", callerId, "RECEIVED", 20, 0L))
                .thenReturn(List.of(projections.createProjection(MercenaryReportView.class, row)));
        when(reportRepository.countMercenaryReports("INDIVIDUAL", callerId, "RECEIVED")).thenReturn(1L);

        PageResponse<MercenaryReportResponse> page = service.listMyReports(callerId, MercenaryReportDirection.RECEIVED,
                1, 20);

        MercenaryReportResponse response = page.data().get(0);
        assertThat(response.canDispute()).isTrue();
        assertThat(response.reliabilityPenalty()).isEqualTo(5);
        assertThat(response.mercenary().displayName()).isEqualTo("Người dùng đã xóa");
        assertThat(page.pagination().totalItems()).isEqualTo(1L);
    }

    @Test
    void teamReportsRequireActiveCaptain() {
        when(teamGuard.requireActiveCaptain(teamId, callerId)).thenThrow(new BusinessException(ErrorCode.TEAM_015));

        assertError(() -> service.listTeamReports(teamId, callerId, MercenaryReportDirection.SENT, 1, 20),
                ErrorCode.TEAM_015);
    }

    private void assertCreateError(Map<String, Object> overrides, String type, ErrorCode code) {
        when(reportRepository.findMercenaryReportContext(applicationId, callerId, type, NOW))
                .thenReturn(Optional.of(ctx(overrides)));
        assertError(() -> service.createReport(applicationId, callerId, new CreateReportRequest(type, "x")), code);
    }

    private MercenaryReportContextView ctx(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("callerDeleted", false);
        values.put("applicationId", applicationId);
        values.put("applicationStatus", "APPROVED");
        values.put("applicantId", applicantId);
        values.put("applicantName", "Mercenary");
        values.put("applicantDeleted", false);
        values.put("matchId", UUID.randomUUID());
        values.put("gameEnded", true);
        values.put("gameStart", NOW.minusHours(3));
        values.put("teamId", teamId);
        values.put("teamStatus", "ACTIVE");
        values.put("teamName", "FC A");
        values.put("callerCaptain", true);
        values.put("callerApplicant", false);
        values.put("callerDisbandedBy", false);
        values.put("hasMessages", false);
        values.put("teamReported", false);
        values.put("applicantReported", false);
        values.putAll(overrides);
        if ("".equals(values.get("applicationId"))) {
            values.put("applicationId", null);
        }
        return projections.createProjection(MercenaryReportContextView.class, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
