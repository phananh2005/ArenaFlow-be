package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ResolveVenueReportRequest;
import com.phananh.arenaflow.application.dto.response.AdminVenueReportResponse;
import com.phananh.arenaflow.application.dto.response.AdminVenueResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.impl.AdminVenueServiceImpl;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.entity.venue.VenueReport;
import com.phananh.arenaflow.domain.projection.AdminVenueReportView;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueReportRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminVenueServiceImplTest {

    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final VenueReportRepository reportRepository = mock(VenueReportRepository.class);
    private final VenueImageRepository imageRepository = mock(VenueImageRepository.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final AdminAuditLog auditLog = mock(AdminAuditLog.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID adminId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    private AdminVenueService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new AdminVenueServiceImpl(new VenueGuard(venueRepository), venueRepository, reportRepository,
                imageRepository, retrier, Mappers.getMapper(VenueMapper.class), new PageRequestFactory(), auditLog);
        when(retrier.execute(eq(ErrorCode.VENUE_011), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    @Test
    void listReportsDefaultsToFilterValueAndMapsUnmaskedNamesWithDeletedFlags() {
        AdminVenueReportView view = reportView("PENDING", true);
        when(reportRepository.findAdminReports(eq("PENDING"), eq(venueId), any()))
                .thenReturn(new PageImpl<>(List.of(view), PageRequest.of(0, 20), 1));

        PageResponse<AdminVenueReportResponse> page = service.listReports(VenueReportStatusFilter.PENDING, venueId, 1, 20);

        AdminVenueReportResponse report = page.data().get(0);
        assertThat(report.reporterName()).isEqualTo("Real Reporter");
        assertThat(report.isReporterDeleted()).isTrue();
        assertThat(report.handledByName()).isNull();
        assertThat(report.isHandledByDeleted()).isFalse();
        assertThat(report.createdAt()).isEqualTo(OffsetDateTime.parse("2026-11-19T00:00:00Z"));

        when(reportRepository.findAdminReports(eq(null), eq(null), any())).thenReturn(new PageImpl<>(List.of()));
        service.listReports(VenueReportStatusFilter.ALL, null, 2, 5);
        verify(reportRepository).findAdminReports(eq(null), eq(null), eq(PageRequest.of(1, 5)));
    }

    @Test
    void resolveReportUpdatesPendingReportAndRereadsAdminViewWithAudit() {
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report(VenueReport.Reason.WRONG_INFO,
                VenueReport.Status.PENDING)));
        when(reportRepository.resolveIfPending(eq(reportId), eq("REJECTED"), eq(adminId), any())).thenReturn(1);
        when(reportRepository.findAdminViewById(reportId)).thenReturn(Optional.of(reportView("REJECTED", false)));

        AdminVenueReportResponse response = service.resolveReport(reportId, adminId, new ResolveVenueReportRequest("REJECTED"));

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(venueRepository, never()).reopenClosureAlertIfNoPending(any());
        verify(auditLog).afterCommit(AdminAuditLog.RESOLVE_REPORT, adminId, venueId, reportId);
        verify(retrier).execute(eq(ErrorCode.VENUE_011), any());
    }

    @Test
    void resolvingClosedReportTriesToReopenClosureAlertGateBeforeReread() {
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report(VenueReport.Reason.CLOSED,
                VenueReport.Status.PENDING)));
        when(reportRepository.resolveIfPending(eq(reportId), eq("VERIFIED"), eq(adminId), any())).thenReturn(1);
        when(reportRepository.findAdminViewById(reportId)).thenReturn(Optional.of(reportView("VERIFIED", false)));

        service.resolveReport(reportId, adminId, new ResolveVenueReportRequest("VERIFIED"));

        InOrder order = inOrder(reportRepository, venueRepository);
        order.verify(reportRepository).resolveIfPending(eq(reportId), eq("VERIFIED"), eq(adminId), any());
        order.verify(venueRepository).reopenClosureAlertIfNoPending(venueId);
        order.verify(reportRepository).findAdminViewById(reportId);
    }

    @Test
    void resolveReportReturnsVenue009ForMissingAndVenue010ForHandledOrLostConditionalUpdate() {
        when(reportRepository.findById(reportId)).thenReturn(Optional.empty());
        assertError(() -> service.resolveReport(reportId, adminId, new ResolveVenueReportRequest("VERIFIED")),
                ErrorCode.VENUE_009);

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report(VenueReport.Reason.OTHER,
                VenueReport.Status.VERIFIED)));
        assertError(() -> service.resolveReport(reportId, adminId, new ResolveVenueReportRequest("VERIFIED")),
                ErrorCode.VENUE_010);
        verify(reportRepository, never()).resolveIfPending(any(), any(), any(), any());

        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report(VenueReport.Reason.OTHER,
                VenueReport.Status.PENDING)));
        when(reportRepository.resolveIfPending(eq(reportId), eq("VERIFIED"), eq(adminId), any())).thenReturn(0);
        assertError(() -> service.resolveReport(reportId, adminId, new ResolveVenueReportRequest("VERIFIED")),
                ErrorCode.VENUE_010);
        verifyNoInteractions(auditLog);
    }

    @Test
    void markInactiveSetsStateVerifiesPendingClosureReportsAndReturnsUnmaskedAdminDetail() {
        Venue venue = Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        stubAdminDetail("INACTIVE");

        AdminVenueResponse response = service.markInactive(venueId, adminId);

        assertThat(venue.getStatus()).isEqualTo(Venue.Status.INACTIVE);
        assertThat(venue.getAdminVerifiedInactiveAt()).isNotNull();
        InOrder order = inOrder(venueRepository, reportRepository, imageRepository);
        order.verify(venueRepository).save(venue);
        order.verify(reportRepository).verifyPendingClosureReports(eq(venueId), eq(adminId), any());
        order.verify(venueRepository).findDetailById(venueId);
        assertThat(response.createdByName()).isEqualTo("Real Creator");
        assertThat(response.isCreatedByDeleted()).isTrue();
        verify(auditLog).afterCommit(AdminAuditLog.MARK_INACTIVE, adminId, venueId, null);
    }

    @Test
    void markInactiveReturnsVenue001AndVenue007() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertError(() -> service.markInactive(venueId, adminId), ErrorCode.VENUE_001);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.INACTIVE).build()));
        assertError(() -> service.markInactive(venueId, adminId), ErrorCode.VENUE_007);
        verifyNoInteractions(reportRepository, auditLog);
    }

    @Test
    void reactivateClearsInactiveStateAndBothAlertGatesWithoutTouchingReports() {
        Venue venue = Venue.builder().id(venueId).status(Venue.Status.INACTIVE)
                .adminVerifiedInactiveAt(OffsetDateTime.now()).staleAlertedAt(OffsetDateTime.now())
                .closureAlertedAt(OffsetDateTime.now()).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        stubAdminDetail("ACTIVE");

        AdminVenueResponse response = service.reactivate(venueId, adminId);

        assertThat(venue.getStatus()).isEqualTo(Venue.Status.ACTIVE);
        assertThat(venue.getAdminVerifiedInactiveAt()).isNull();
        assertThat(venue.getStaleAlertedAt()).isNull();
        assertThat(venue.getClosureAlertedAt()).isNull();
        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(venueRepository).save(venue);
        verifyNoInteractions(reportRepository);
        verify(auditLog).afterCommit(AdminAuditLog.REACTIVATE, adminId, venueId, null);
    }

    @Test
    void reactivateReturnsVenue001AndVenue008() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertError(() -> service.reactivate(venueId, adminId), ErrorCode.VENUE_001);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build()));
        assertError(() -> service.reactivate(venueId, adminId), ErrorCode.VENUE_008);
        verifyNoInteractions(auditLog);
    }

    private void stubAdminDetail(String status) {
        Map<String, Object> values = new HashMap<>(Map.of("venueId", venueId, "venueName", "Venue", "address", "Address",
                "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", status, "createdByType", "USER",
                "createdByName", "Real Creator", "createdByDeleted", true));
        when(venueRepository.findDetailById(venueId)).thenReturn(Optional.of(
                projections.createProjection(VenueDetailView.class, values)));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(List.of());
    }

    private VenueReport report(VenueReport.Reason reason, VenueReport.Status status) {
        return VenueReport.builder().id(reportId).venueId(venueId).reporterId(UUID.randomUUID())
                .reason(reason).status(status).build();
    }

    private AdminVenueReportView reportView(String status, boolean reporterDeleted) {
        Map<String, Object> values = new HashMap<>();
        values.put("reportId", reportId);
        values.put("venueId", venueId);
        values.put("venueName", "Venue");
        values.put("venueStatus", "ACTIVE");
        values.put("reporterId", UUID.randomUUID());
        values.put("reporterName", "Real Reporter");
        values.put("reporterDeleted", reporterDeleted);
        values.put("reason", "CLOSED");
        values.put("status", status);
        values.put("handledByDeleted", false);
        values.put("createdAt", Instant.parse("2026-11-19T00:00:00Z"));
        return projections.createProjection(AdminVenueReportView.class, values);
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
