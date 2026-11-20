package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateVenueRequest;
import com.phananh.arenaflow.application.dto.request.ReportVenueRequest;
import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import com.phananh.arenaflow.application.dto.request.UpdateVenueRequest;
import com.phananh.arenaflow.application.dto.response.VenueReportResponse;
import com.phananh.arenaflow.application.dto.response.VenueResponse;
import com.phananh.arenaflow.application.event.venue.VenueAdminAlertEvent;
import com.phananh.arenaflow.application.event.venue.VenueAlertType;
import com.phananh.arenaflow.application.mapper.VenueMapper;
import com.phananh.arenaflow.application.service.impl.VenueServiceImpl;
import com.phananh.arenaflow.application.support.AdminAuditLog;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.VenueGuard;
import com.phananh.arenaflow.application.support.security.CurrentActor;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.domain.entity.venue.Venue;
import com.phananh.arenaflow.domain.entity.venue.VenueReport;
import com.phananh.arenaflow.domain.projection.VenueDetailView;
import com.phananh.arenaflow.domain.projection.VenueSummaryView;
import com.phananh.arenaflow.domain.projection.WardView;
import com.phananh.arenaflow.domain.repository.AdministrativeUnitRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import com.phananh.arenaflow.domain.repository.VenueImageRepository;
import com.phananh.arenaflow.domain.repository.VenueReportRepository;
import com.phananh.arenaflow.domain.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VenueServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdministrativeUnitRepository unitRepository = mock(AdministrativeUnitRepository.class);
    private final VenueRepository venueRepository = mock(VenueRepository.class);
    private final VenueImageRepository imageRepository = mock(VenueImageRepository.class);
    private final VenueReportRepository reportRepository = mock(VenueReportRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AdminAuditLog auditLog = mock(AdminAuditLog.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final VenueMapper venueMapper = Mappers.getMapper(VenueMapper.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final UUID callerId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID wardId = UUID.randomUUID();

    private VenueServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new VenueServiceImpl(userRepository, unitRepository, venueRepository, imageRepository, reportRepository,
                venueMapper, new PageRequestFactory(), new VenueGuard(venueRepository), retrier, eventPublisher, auditLog);
        when(retrier.execute(eq(ErrorCode.VENUE_011), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
        when(venueRepository.save(any(Venue.class))).thenAnswer(invocation -> {
            Venue venue = invocation.getArgument(0);
            ReflectionTestUtils.setField(venue, "id", venueId);
            return venue;
        });
    }

    @Test
    void createVenueChecksUserThenWardAndBuildsResponseWithoutReread() {
        User user = User.builder().id(callerId).displayName("Creator").role(User.Role.ADMIN).build();
        WardView ward = projections.createProjection(WardView.class, Map.of(
                "wardId", wardId, "wardName", "Ward", "provinceId", UUID.randomUUID(), "provinceName", "Province"));
        when(userRepository.findByIdAndDeletedAtIsNull(callerId)).thenReturn(Optional.of(user));
        when(unitRepository.findWardWithProvince(wardId)).thenReturn(Optional.of(ward));

        VenueResponse response = service.createVenue(callerId, new CreateVenueRequest("  Venue  ", "  Address  ",
                new BigDecimal("10.1"), new BigDecimal("106.1"), wardId, "", "", ""));

        ArgumentCaptor<Venue> captured = ArgumentCaptor.forClass(Venue.class);
        verify(venueRepository).save(captured.capture());
        Venue saved = captured.getValue();
        assertThat(saved.getName()).isEqualTo("Venue");
        assertThat(saved.getAddress()).isEqualTo("Address");
        assertThat(saved.getVenueType()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getWebsite()).isNull();
        assertThat(saved.getCreatedByType()).isEqualTo(Venue.CreatedByType.ADMIN);
        assertThat(saved.getCreatedByUserId()).isEqualTo(callerId);
        assertThat(response.venueId()).isEqualTo(venueId);
        assertThat(response.createdByName()).isEqualTo("Creator");
        assertThat(response.ward().wardName()).isEqualTo("Ward");
        assertThat(response.images()).isEmpty();
    }

    @Test
    void createVenueReturnsUser001BeforeWardLookupForDeletedCaller() {
        when(userRepository.findByIdAndDeletedAtIsNull(callerId)).thenReturn(Optional.empty());

        assertError(() -> service.createVenue(callerId, request()), ErrorCode.USER_001);
    }

    @Test
    void createVenueReturnsUser002ForUnknownOrNonWard() {
        User user = User.builder().id(callerId).displayName("Creator").build();
        when(userRepository.findByIdAndDeletedAtIsNull(callerId)).thenReturn(Optional.of(user));
        when(unitRepository.findWardWithProvince(wardId)).thenReturn(Optional.empty());

        assertError(() -> service.createVenue(callerId, request()), ErrorCode.USER_002);
    }

    @Test
    void listVenuesSelectsIndexAwareQueryAndMapsPaginatedSummary() {
        VenueSummaryView summary = projections.createProjection(VenueSummaryView.class, Map.of(
                "venueId", venueId, "venueName", "Hoa Lư", "address", "Address",
                "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", "ACTIVE"));
        when(venueRepository.searchByStatus(eq("ACTIVE"), eq("%hoa%"), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(1, 1), 3));
        when(venueRepository.searchAll(eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        when(venueRepository.searchNearbyActive(eq(null), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of(summary), PageRequest.of(0, 20), 1));
        when(venueRepository.searchNearbyAny(eq("INACTIVE"), eq(null), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var byStatus = service.listVenues(new SearchVenueRequest(" hoa ", null, null, null, null, 2, 1));
        var all = service.listVenues(new SearchVenueRequest(null, VenueStatusFilter.ALL, null, null, null, null, null));
        var nearbyActive = service.listVenues(new SearchVenueRequest(null, null,
                new BigDecimal("10.78"), new BigDecimal("106.70"), null, null, null));
        var nearbyInactive = service.listVenues(new SearchVenueRequest(null, VenueStatusFilter.INACTIVE,
                new BigDecimal("10.78"), new BigDecimal("106.70"), new BigDecimal("3"), null, null));

        assertThat(byStatus.data()).extracting(row -> row.venueName()).containsExactly("Hoa Lư");
        assertThat(byStatus.pagination().totalItems()).isEqualTo(3);
        assertThat(all.data()).isEmpty();
        assertThat(nearbyActive.data()).hasSize(1);
        assertThat(nearbyInactive.data()).isEmpty();
        verify(venueRepository).searchByStatus(eq("ACTIVE"), eq("%hoa%"), any());
        verify(venueRepository).searchAll(eq(null), any());
        verify(venueRepository).searchNearbyActive(eq(null), eq(10.78d), eq(106.70d), eq(5d), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), any());
        verify(venueRepository).searchNearbyAny(eq("INACTIVE"), eq(null), eq(10.78d), eq(106.70d), eq(3d),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void updateVenueChecksGuardsThenAppliesRequestedFieldsAndRereadsDetail() {
        Venue venue = Venue.builder().id(venueId).name("Old").address("Old address").latitude(BigDecimal.ONE)
                .longitude(BigDecimal.ONE).wardId(wardId).createdByUserId(callerId).status(Venue.Status.ACTIVE)
                .venueType(Venue.VenueType.SEVEN_A_SIDE).phone("0123456789").website("https://old.test").build();
        WardView replacementWard = projections.createProjection(WardView.class, Map.of(
                "wardId", UUID.randomUUID(), "wardName", "Ward", "provinceId", UUID.randomUUID(), "provinceName", "Province"));
        VenueDetailView detail = projections.createProjection(VenueDetailView.class, Map.of(
                "venueId", venueId, "venueName", "New", "address", "New address", "latitude", BigDecimal.TEN,
                "longitude", BigDecimal.valueOf(20), "status", "ACTIVE", "createdByType", "USER"));
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(venue));
        when(unitRepository.findWardWithProvince(replacementWard.getWardId())).thenReturn(Optional.of(replacementWard));
        when(venueRepository.findDetailById(venueId)).thenReturn(Optional.of(detail));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(List.of());

        VenueResponse response = service.updateVenue(venueId, new CurrentActor(callerId, false),
                new UpdateVenueRequest(" New ", " New address ", BigDecimal.TEN, BigDecimal.valueOf(20),
                        replacementWard.getWardId(), "", "", ""));

        assertThat(venue.getName()).isEqualTo("New");
        assertThat(venue.getAddress()).isEqualTo("New address");
        assertThat(venue.getLatitude()).isEqualByComparingTo("10");
        assertThat(venue.getLongitude()).isEqualByComparingTo("20");
        assertThat(venue.getWardId()).isEqualTo(replacementWard.getWardId());
        assertThat(venue.getVenueType()).isNull();
        assertThat(venue.getPhone()).isNull();
        assertThat(venue.getWebsite()).isNull();
        verify(venueRepository).save(venue);
        verify(retrier).execute(eq(ErrorCode.VENUE_011), any());
        assertThat(response.venueName()).isEqualTo("New");
    }

    @Test
    void updateVenueAppliesDocumentedGuardOrder() {
        CurrentActor owner = new CurrentActor(callerId, false);
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_001);

        Venue inactiveOtherOwner = Venue.builder().createdByUserId(UUID.randomUUID()).status(Venue.Status.INACTIVE).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(inactiveOtherOwner));
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_013);

        Venue inactiveOwner = Venue.builder().createdByUserId(callerId).status(Venue.Status.INACTIVE).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(inactiveOwner));
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_002);

        Venue legacy = Venue.builder().createdByUserId(callerId).status(Venue.Status.ACTIVE).wardId(null).build();
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(legacy));
        assertError(() -> service.updateVenue(venueId, owner, new UpdateVenueRequest(null, null, null, null, null, null, null, null)),
                ErrorCode.VENUE_016);
    }

    @Test
    void getVenueReturnsDetailWithImagesOrVenue001() {
        VenueDetailView view = projections.createProjection(VenueDetailView.class,
                Map.of("venueId", venueId, "venueName", "Venue", "address", "Address",
                        "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", "ACTIVE",
                        "createdByType", "USER"));
        when(venueRepository.findDetailById(venueId)).thenReturn(Optional.of(view));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(List.of());

        assertThat(service.getVenue(venueId).venueName()).isEqualTo("Venue");

        UUID missing = UUID.randomUUID();
        when(venueRepository.findDetailById(missing)).thenReturn(Optional.empty());
        assertError(() -> service.getVenue(missing), ErrorCode.VENUE_001);
    }

    @Test
    void listVenuesEscapesLikeWildcardsAndBackslashInKeyword() {
        when(venueRepository.searchByStatus(eq("ACTIVE"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.listVenues(new SearchVenueRequest("100%_a\\b", null, null, null, null, null, null));

        verify(venueRepository).searchByStatus(eq("ACTIVE"), eq("%100\\%\\_a\\\\b%"), any());
    }

    @Test
    void reportVenueRejectsAdminBeforeAnyDatabaseAccess() {
        assertError(() -> service.reportVenue(venueId, new CurrentActor(callerId, true),
                new ReportVenueRequest("CLOSED", null)), ErrorCode.VENUE_015);
        verifyNoInteractions(venueRepository, reportRepository, eventPublisher);
    }

    @Test
    void reportVenueRequiresExistingActiveVenue() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.empty());
        assertError(() -> service.reportVenue(venueId, new CurrentActor(callerId, false),
                new ReportVenueRequest("CLOSED", null)), ErrorCode.VENUE_001);

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.INACTIVE).build()));
        assertError(() -> service.reportVenue(venueId, new CurrentActor(callerId, false),
                new ReportVenueRequest("CLOSED", null)), ErrorCode.VENUE_002);
        verifyNoInteractions(reportRepository);
    }

    @Test
    void reportVenueStoresPendingReportWithoutClosureGateForOtherReasons() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).name("Venue").status(Venue.Status.ACTIVE).build()));
        stubSavedReport();

        VenueReportResponse response = service.reportVenue(venueId, new CurrentActor(callerId, false),
                new ReportVenueRequest("WRONG_INFO", "Wrong phone"));

        ArgumentCaptor<VenueReport> captured = ArgumentCaptor.forClass(VenueReport.class);
        verify(reportRepository).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getReporterId()).isEqualTo(callerId);
        assertThat(captured.getValue().getReason()).isEqualTo(VenueReport.Reason.WRONG_INFO);
        assertThat(captured.getValue().getStatus()).isEqualTo(VenueReport.Status.PENDING);
        assertThat(response.venueId()).isEqualTo(venueId);
        assertThat(response.reason()).isEqualTo("WRONG_INFO");
        assertThat(response.detail()).isEqualTo("Wrong phone");
        assertThat(response.status()).isEqualTo("PENDING");
        verify(venueRepository, never()).markClosureAlerted(any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void firstClosedReportOpensClosureGateAndPublishesSingleAdminAlert() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).name("Venue").status(Venue.Status.ACTIVE).build()));
        stubSavedReport();
        when(venueRepository.markClosureAlerted(eq(venueId), any())).thenReturn(1);

        service.reportVenue(venueId, new CurrentActor(callerId, false), new ReportVenueRequest("CLOSED", null));

        ArgumentCaptor<VenueAdminAlertEvent> event = ArgumentCaptor.forClass(VenueAdminAlertEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().venueId()).isEqualTo(venueId);
        assertThat(event.getValue().venueName()).isEqualTo("Venue");
        assertThat(event.getValue().alertType()).isEqualTo(VenueAlertType.CLOSURE_REPORTED);
        assertThat(event.getValue().triggeredAt()).isNotNull();
    }

    @Test
    void closedReportDoesNotAlertAgainWhenGateIsAlreadyClosedOrLostRace() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(Venue.builder().id(venueId).name("Venue")
                .status(Venue.Status.ACTIVE).closureAlertedAt(OffsetDateTime.now()).build()));
        stubSavedReport();
        service.reportVenue(venueId, new CurrentActor(callerId, false), new ReportVenueRequest("CLOSED", null));
        verify(venueRepository, never()).markClosureAlerted(any(), any());

        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).name("Venue").status(Venue.Status.ACTIVE).build()));
        when(venueRepository.markClosureAlerted(eq(venueId), any())).thenReturn(0);
        service.reportVenue(venueId, new CurrentActor(callerId, false), new ReportVenueRequest("CLOSED", null));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void duplicatePendingReportMapsToVenue014() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build()));
        when(reportRepository.saveAndFlush(any(VenueReport.class)))
                .thenThrow(DataIntegrityViolationFixtures.violation("uq_venue_report_pending"));

        assertError(() -> service.reportVenue(venueId, new CurrentActor(callerId, false),
                new ReportVenueRequest("CLOSED", null)), ErrorCode.VENUE_014);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void otherIntegrityViolationOnReportIsRethrownNotMappedToVenue014() {
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(
                Venue.builder().id(venueId).status(Venue.Status.ACTIVE).build()));
        DataIntegrityViolationException fkViolation = DataIntegrityViolationFixtures.violation("venue_report_reporter_id_fkey");
        when(reportRepository.saveAndFlush(any(VenueReport.class))).thenThrow(fkViolation);

        assertThatThrownBy(() -> service.reportVenue(venueId, new CurrentActor(callerId, false),
                new ReportVenueRequest("CLOSED", null))).isSameAs(fkViolation);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void adminUpdatingOthersVenueRegistersAuditInsideRetriedTransaction() {
        stubUpdatableVenue(UUID.randomUUID());

        service.updateVenue(venueId, new CurrentActor(callerId, true), emptyUpdate());

        verify(auditLog).afterCommit(AdminAuditLog.ADMIN_UPDATE_VENUE, callerId, venueId, null);
    }

    @Test
    void ownerOrAdminCreatorUpdateWritesNoAudit() {
        stubUpdatableVenue(callerId);

        service.updateVenue(venueId, new CurrentActor(callerId, false), emptyUpdate());
        service.updateVenue(venueId, new CurrentActor(callerId, true), emptyUpdate());

        verifyNoInteractions(auditLog);
    }

    private void stubUpdatableVenue(UUID creatorId) {
        when(venueRepository.findById(venueId)).thenReturn(Optional.of(Venue.builder().id(venueId).wardId(wardId)
                .createdByUserId(creatorId).status(Venue.Status.ACTIVE).build()));
        when(venueRepository.findDetailById(venueId)).thenReturn(Optional.of(projections.createProjection(
                VenueDetailView.class, Map.of("venueId", venueId, "venueName", "Venue", "address", "Address",
                        "latitude", BigDecimal.ONE, "longitude", BigDecimal.ONE, "status", "ACTIVE",
                        "createdByType", "USER"))));
        when(imageRepository.findByVenueIdOrderByDisplayOrderAsc(venueId)).thenReturn(List.of());
    }

    private static UpdateVenueRequest emptyUpdate() {
        return new UpdateVenueRequest(null, null, null, null, null, null, null, null);
    }

    private void stubSavedReport() {
        when(reportRepository.saveAndFlush(any(VenueReport.class))).thenAnswer(invocation -> {
            VenueReport report = invocation.getArgument(0);
            ReflectionTestUtils.setField(report, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(report, "createdAt", OffsetDateTime.parse("2026-11-19T00:00:00Z"));
            return report;
        });
    }

    private CreateVenueRequest request() {
        return new CreateVenueRequest("Venue", "Address", BigDecimal.ONE, BigDecimal.ONE, wardId,
                null, null, null);
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
