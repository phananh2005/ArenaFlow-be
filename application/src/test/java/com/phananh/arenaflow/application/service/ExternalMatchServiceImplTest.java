package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import com.phananh.arenaflow.application.dto.request.UpdateExternalMatchRequest;
import com.phananh.arenaflow.application.dto.response.ExternalMatchCancellationResponse;
import com.phananh.arenaflow.application.dto.response.ExternalMatchResponse;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.mapper.MercenaryMapper;
import com.phananh.arenaflow.application.service.impl.ExternalMatchServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.mercenary.MercenaryGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.mercenary.ExternalMatch;
import com.phananh.arenaflow.domain.projection.ExternalMatchContextView;
import com.phananh.arenaflow.domain.projection.GameCancelNoticeView;
import com.phananh.arenaflow.domain.projection.RecruitmentRowView;
import com.phananh.arenaflow.domain.projection.RejectedApplicationView;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.repository.ExternalMatchRepository;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalMatchServiceImplTest {

    private static final LocalDateTime NOW = AppTime.now();

    private final ExternalMatchRepository externalMatchRepository = mock(ExternalMatchRepository.class);
    private final MercenaryRecruitmentRepository recruitmentRepository = mock(MercenaryRecruitmentRepository.class);
    private final MercenaryApplicationRepository applicationRepository = mock(MercenaryApplicationRepository.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final VenueSelectionService venueSelectionService = mock(VenueSelectionService.class);
    private final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final UUID callerId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();
    private final UUID externalId = UUID.randomUUID();

    private ExternalMatchServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new ExternalMatchServiceImpl(externalMatchRepository, recruitmentRepository, applicationRepository,
                teamGuard, new MercenaryGuard(), venueSelectionService, retrier,
                Mappers.getMapper(MercenaryMapper.class), new PageRequestFactory(), eventPublisher, validator, clock);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    @Test
    void createDeclaresMatchInsertsPositionsAndMarksVenueSelected() {
        when(teamGuard.requireActiveCaptain(teamId, callerId)).thenReturn(projections.createProjection(
                TeamCaptainContextView.class, Map.of("status", "ACTIVE", "captain", true, "name", "FC A",
                        "userDisplayName", "Captain", "userDeleted", false)));
        when(venueSelectionService.requireSelectable(List.of(venueId))).thenReturn(List.of(venue(venueId)));
        when(externalMatchRepository.save(any(ExternalMatch.class))).thenAnswer(invocation -> {
            ExternalMatch match = invocation.getArgument(0);
            ReflectionTestUtils.setField(match, "id", externalId);
            ReflectionTestUtils.setField(match, "createdAt", NOW);
            return match;
        });
        when(recruitmentRepository.insertAll(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of(
                projections.createProjection(RecruitmentRowView.class, Map.of("id", UUID.randomUUID(),
                        "position", "Tiền vệ", "quantityNeeded", 2, "createdAt", NOW))));
        LocalDateTime start = NOW.plusHours(3);

        ExternalMatchResponse response = service.createExternalMatch(teamId, callerId, new CreateExternalMatchRequest(
                venueId, start, start.plusHours(2), List.of(), " FC Phường 5 ",
                List.of(new RecruitmentPositionRequest("Tiền vệ", 2))));

        verify(recruitmentRepository).insertAll(eq(null), eq(externalId), eq(teamId), eq(callerId), any(), any(),
                eq(NOW));
        verify(venueSelectionService).markSelected(List.of(venueId), NOW);
        assertThat(response.status()).isEqualTo("SCHEDULED");
        assertThat(response.opponentName()).isEqualTo("FC Phường 5");
        assertThat(response.hasApprovedMercenary()).isFalse();
        assertThat(response.recruitments()).hasSize(1);
        assertThat(response.recruitments().get(0).match().source()).isEqualTo("EXTERNAL");
        assertThat(response.recruitments().get(0).match().externalMatchId()).isEqualTo(externalId);
    }

    @Test
    void getChecksExistenceThenDisbandedThenCaptain() {
        when(externalMatchRepository.findDetail(externalId, callerId)).thenReturn(Optional.empty());
        assertError(() -> service.getExternalMatch(externalId, callerId), ErrorCode.MERCENARY_024);

        when(externalMatchRepository.findDetail(externalId, callerId))
                .thenReturn(Optional.of(context(Map.of("teamStatus", "DISBANDED"))));
        assertError(() -> service.getExternalMatch(externalId, callerId), ErrorCode.TEAM_016);

        when(externalMatchRepository.findDetail(externalId, callerId))
                .thenReturn(Optional.of(context(Map.of("teamStatus", "INACTIVE", "callerCaptain", false))));
        assertError(() -> service.getExternalMatch(externalId, callerId), ErrorCode.TEAM_002);
    }

    @Test
    void updateOpponentOnlySkipsVenueAndRecruitmentLocks() {
        ExternalMatchContextView ctx = context(Map.of());
        when(externalMatchRepository.findContext(externalId, callerId)).thenReturn(Optional.of(ctx));
        when(externalMatchRepository.updateIfVersion(eq(externalId), eq(4L), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ExternalMatchResponse response = service.updateExternalMatch(externalId, callerId,
                new UpdateExternalMatchRequest(null, null, null, null, "FC Mới"));

        assertThat(response.opponentName()).isEqualTo("FC Mới");
        assertThat(response.updatedAt()).isEqualTo(NOW);
        verify(venueSelectionService, never()).requireSelectable(any());
        verify(recruitmentRepository, never()).lockByExternalMatch(any(), any());
        verify(venueSelectionService, never()).markSelected(any(Collection.class), any(LocalDateTime.class));
    }

    @Test
    void updateVenueWhenMercenaryApprovedIsRejectedBeforeVenueLookup() {
        when(externalMatchRepository.findContext(externalId, callerId))
                .thenReturn(Optional.of(context(Map.of("hasApprovedMercenary", true))));

        assertError(() -> service.updateExternalMatch(externalId, callerId,
                new UpdateExternalMatchRequest(UUID.randomUUID(), null, null, null, null)), ErrorCode.MERCENARY_025);
        verify(venueSelectionService, never()).requireSelectable(any());
        verify(externalMatchRepository, never()).updateIfVersion(any(), anyLong(), any(), any(), any(), any(), any(),
                any());
    }

    @Test
    void updateVenueRechecksApprovalOnLockedRecruitments() {
        UUID newVenue = UUID.randomUUID();
        when(externalMatchRepository.findContext(externalId, callerId)).thenReturn(Optional.of(context(Map.of())));
        when(venueSelectionService.requireSelectable(List.of(newVenue))).thenReturn(List.of(venue(newVenue)));
        when(externalMatchRepository.updateIfVersion(any(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(recruitmentRepository.lockByExternalMatch(externalId, NOW)).thenReturn(List.of(0, 1));

        assertError(() -> service.updateExternalMatch(externalId, callerId,
                new UpdateExternalMatchRequest(newVenue, null, null, null, null)), ErrorCode.MERCENARY_025);
        verify(venueSelectionService, never()).markSelected(any(Collection.class), any(LocalDateTime.class));
    }

    @Test
    void updateVenueMarksNewVenueSelected() {
        UUID newVenue = UUID.randomUUID();
        when(externalMatchRepository.findContext(externalId, callerId)).thenReturn(Optional.of(context(Map.of())));
        when(venueSelectionService.requireSelectable(List.of(newVenue))).thenReturn(List.of(venue(newVenue)));
        when(externalMatchRepository.updateIfVersion(any(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(recruitmentRepository.lockByExternalMatch(externalId, NOW)).thenReturn(List.of(0));

        ExternalMatchResponse response = service.updateExternalMatch(externalId, callerId,
                new UpdateExternalMatchRequest(newVenue, null, null, null, null));

        assertThat(response.venue().venueId()).isEqualTo(newVenue);
        verify(venueSelectionService).markSelected(List.of(newVenue), NOW);
    }

    @Test
    void updateTimesAfterStartIsRejected() {
        when(externalMatchRepository.findContext(externalId, callerId))
                .thenReturn(Optional.of(context(Map.of("startsAt", NOW.minusMinutes(1)))));

        assertError(() -> service.updateExternalMatch(externalId, callerId,
                new UpdateExternalMatchRequest(null, null, null, List.of("Trung bình"), null)), ErrorCode.MERCENARY_013);
    }

    @Test
    void updateCasMissSignalsRetry() {
        when(externalMatchRepository.findContext(externalId, callerId)).thenReturn(Optional.of(context(Map.of())));
        when(externalMatchRepository.updateIfVersion(any(), anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.updateExternalMatch(externalId, callerId,
                new UpdateExternalMatchRequest(null, null, null, null, "X")))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void cancelAfterStartIsRejected() {
        when(externalMatchRepository.findContext(externalId, callerId))
                .thenReturn(Optional.of(context(Map.of("startsAt", NOW))));

        assertError(() -> service.cancelExternalMatch(externalId, callerId), ErrorCode.MERCENARY_013);
    }

    @Test
    void cancelClosesRecruitmentsRejectsPendingAndNotifiesApproved() {
        UUID recruitmentId = UUID.randomUUID();
        UUID rejectedId = UUID.randomUUID();
        UUID approvedId = UUID.randomUUID();
        when(externalMatchRepository.findContext(externalId, callerId))
                .thenReturn(Optional.of(context(Map.of("teamStatus", "INACTIVE"))));
        when(externalMatchRepository.cancelIfScheduled(externalId, NOW)).thenReturn(1);
        when(recruitmentRepository.closeActiveByExternalMatch(externalId, NOW)).thenReturn(List.of(recruitmentId));
        when(applicationRepository.rejectPendingByRecruitmentIds(any(), eq(NOW))).thenReturn(List.of(
                projections.createProjection(RejectedApplicationView.class,
                        Map.of("id", rejectedId, "recruitmentId", recruitmentId))));
        Map<String, Object> notice = new HashMap<>();
        notice.put("id", approvedId);
        notice.put("externalMatchId", externalId);
        when(applicationRepository.markGameCancelNotifiedByExternalMatch(externalId, NOW)).thenReturn(List.of(
                projections.createProjection(GameCancelNoticeView.class, notice)));

        ExternalMatchCancellationResponse response = service.cancelExternalMatch(externalId, callerId);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.closedRecruitmentCount()).isEqualTo(1);
        assertThat(response.rejectedApplicationCount()).isEqualTo(1);
        ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).anySatisfy(event -> {
            assertThat(event).isInstanceOf(MercenaryRecruitmentClosedEvent.class);
            MercenaryRecruitmentClosedEvent closed = (MercenaryRecruitmentClosedEvent) event;
            assertThat(closed.cause()).isEqualTo(MercenaryRecruitmentClosedEvent.Cause.EXTERNAL_MATCH_CANCELLED);
            assertThat(closed.rejectedApplicationIds()).containsExactly(rejectedId);
        });
        assertThat(events.getAllValues()).anySatisfy(event -> {
            assertThat(event).isInstanceOf(MercenaryGameCancelledEvent.class);
            assertThat(((MercenaryGameCancelledEvent) event).applicationIds()).containsExactly(approvedId);
        });
    }

    @Test
    void cancelCasMissSignalsRetry() {
        when(externalMatchRepository.findContext(externalId, callerId)).thenReturn(Optional.of(context(Map.of())));
        when(externalMatchRepository.cancelIfScheduled(externalId, NOW)).thenReturn(0);

        assertThatThrownBy(() -> service.cancelExternalMatch(externalId, callerId))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    private ExternalMatchContextView context(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("externalMatchId", externalId);
        values.put("teamId", teamId);
        values.put("teamStatus", "ACTIVE");
        values.put("callerCaptain", true);
        values.put("status", "SCHEDULED");
        values.put("startsAt", NOW.plusDays(1));
        values.put("endsAt", NOW.plusDays(1).plusHours(2));
        values.put("skillLevels", "[]");
        values.put("opponentName", "FC Cũ");
        values.put("venueId", venueId);
        values.put("venueName", "Sân A");
        values.put("hasApprovedMercenary", false);
        values.put("version", 4L);
        values.put("createdAt", NOW.minusDays(2));
        values.put("updatedAt", NOW.minusDays(2));
        values.putAll(overrides);
        return projections.createProjection(ExternalMatchContextView.class, values);
    }

    private SelectableVenueView venue(UUID id) {
        return projections.createProjection(SelectableVenueView.class, Map.of("id", id, "name", "Sân",
                "address", "Địa chỉ", "latitude", BigDecimal.ONE, "longitude", BigDecimal.TEN, "status", "ACTIVE",
                "wardName", "Phường 1", "provinceName", "TP HCM"));
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
