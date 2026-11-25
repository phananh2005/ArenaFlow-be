package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CancelMatchRequest;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.CreateMatchRequest;
import com.phananh.arenaflow.application.dto.request.UpdateCostAgreementRequest;
import com.phananh.arenaflow.application.dto.response.CostAgreementResponse;
import com.phananh.arenaflow.application.dto.response.CostCustomItemResponse;
import com.phananh.arenaflow.application.dto.response.MatchCancellationResponse;
import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.support.CancellationResult;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.application.dto.request.SearchMatchRequest;
import com.phananh.arenaflow.application.dto.request.TimeOptionRequest;
import com.phananh.arenaflow.application.dto.response.MatchResponse;
import com.phananh.arenaflow.application.mapper.MatchMapper;
import com.phananh.arenaflow.application.service.impl.MatchServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.GeoBoundingBox;
import com.phananh.arenaflow.application.support.MatchCancellation;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchGuard;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.Sport;
import com.phananh.arenaflow.domain.projection.MatchDetailView;
import com.phananh.arenaflow.domain.projection.MatchOptionRowView;
import com.phananh.arenaflow.domain.projection.MatchSummaryView;
import com.phananh.arenaflow.domain.projection.SelectableVenueView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.projection.TimeOptionView;
import com.phananh.arenaflow.domain.repository.CostCustomItemRepository;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import com.phananh.arenaflow.domain.repository.MatchTimeOptionRepository;
import com.phananh.arenaflow.domain.repository.MatchVenueOptionRepository;
import com.phananh.arenaflow.domain.repository.PairingRepository;
import com.phananh.arenaflow.domain.repository.SportRepository;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Tạo / tìm / xem kèo, kèo của đội, thoả thuận chi phí và huỷ kèo (luồng A, luồng B). */
class MatchServiceImplTest {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    final MatchRepository matchRepository = mock(MatchRepository.class);
    final CostCustomItemRepository costItemRepository = mock(CostCustomItemRepository.class);
    final SportRepository sportRepository = mock(SportRepository.class);
    final TeamRepository teamRepository = mock(TeamRepository.class);
    final MatchRequestRepository requestRepository = mock(MatchRequestRepository.class);
    final MatchVenueOptionRepository venueOptionRepository = mock(MatchVenueOptionRepository.class);
    final MatchTimeOptionRepository timeOptionRepository = mock(MatchTimeOptionRepository.class);
    final PairingRepository pairingRepository = mock(PairingRepository.class);
    final MatchGuard matchGuard = new MatchGuard(matchRepository);
    final TeamGuard teamGuard = mock(TeamGuard.class);
    final MatchExpiry matchExpiry = mock(MatchExpiry.class);
    final MatchCancellation matchCancellation = mock(MatchCancellation.class);
    final VenueSelectionService venueSelectionService = mock(VenueSelectionService.class);
    final ConcurrencyConflictRetrier retrier = mock(ConcurrencyConflictRetrier.class);
    final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault());
    final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    final UUID callerId = UUID.randomUUID();
    final UUID teamId = UUID.randomUUID();
    final UUID sportId = UUID.randomUUID();
    final UUID venueId = UUID.randomUUID();
    MatchServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new MatchServiceImpl(matchRepository, costItemRepository, sportRepository, teamRepository,
                requestRepository, venueOptionRepository, timeOptionRepository, pairingRepository, matchGuard, teamGuard, matchExpiry, matchCancellation, venueSelectionService, retrier,
                Mappers.getMapper(MatchMapper.class), new PageRequestFactory(), publisher, clock);
        when(retrier.execute(eq(ErrorCode.MATCH_014), any()))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(1)).get());
    }

    static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    MatchDetailView detailView(UUID matchId, String type) {
        Map<String, Object> row = new HashMap<>();
        row.put("matchId", matchId);
        row.put("type", type);
        row.put("effectiveStatus", "OPEN");
        row.put("skillLevels", "[]");
        row.put("matchStartTime", NOW.plusDays(1));
        row.put("authorTeamId", teamId);
        row.put("venueId", venueId);
        return projections.createProjection(MatchDetailView.class, row);
    }

    private CreateMatchRequest createRequest(String type) {
        LocalDateTime start = NOW.plusDays(1);
        return new CreateMatchRequest(type, teamId, sportId, venueId, null, "SEVEN_A_SIDE", start, start.plusHours(2),
                null, start.minusHours(5), null, "  U23 ", "  ");
    }

    private CreateMatchRequest needVenueRequest(List<UUID> venueOptionIds, LocalDateTime earliest) {
        return new CreateMatchRequest("NEED_VENUE", teamId, sportId, null, venueOptionIds, "SEVEN_A_SIDE", null, null,
                List.of(new TimeOptionRequest(earliest.plusHours(2), earliest.plusHours(4)),
                        new TimeOptionRequest(earliest, earliest.plusHours(2))),
                earliest.minusHours(5), null, " Giao hữu ", null);
    }

    private <T> T projection(Class<T> type, Map<String, Object> row) {
        return projections.createProjection(type, row);
    }

    @Test
    void createNeedVenueSavesEarliestSlotInsertsOptionsAndBuildsResponseWithoutReadBack() {
        UUID matchId = UUID.randomUUID();
        UUID venueY = UUID.randomUUID();
        UUID venueZ = UUID.randomUUID();
        LocalDateTime earliest = NOW.plusDays(1);
        when(teamGuard.requireActiveCaptain(teamId, callerId)).thenReturn(projection(TeamCaptainContextView.class,
                Map.of("name", "Team Alpha", "reliabilityScore", 80)));
        when(sportRepository.findById(sportId))
                .thenReturn(Optional.of(Sport.builder().id(sportId).name("Bóng đá").isActive(true).build()));
        when(venueSelectionService.requireSelectable(List.of(venueY, venueZ))).thenReturn(List.of(
                projection(SelectableVenueView.class, Map.of("id", venueY, "name", "Sân Y", "status", "ACTIVE")),
                projection(SelectableVenueView.class, Map.of("id", venueZ, "name", "Sân Z", "status", "ACTIVE"))));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> {
            Match match = invocation.getArgument(0);
            match.setId(matchId);
            return match;
        });
        UUID earlyOption = UUID.randomUUID();
        UUID lateOption = UUID.randomUUID();
        when(timeOptionRepository.insertAllReturning(eq(matchId), any(), any(), eq(NOW))).thenReturn(List.of(
                projection(TimeOptionView.class, Map.of("id", lateOption, "proposedStartTime", earliest.plusHours(2),
                        "proposedEndTime", earliest.plusHours(4))),
                projection(TimeOptionView.class, Map.of("id", earlyOption, "proposedStartTime", earliest,
                        "proposedEndTime", earliest.plusHours(2)))));

        MatchResponse response = service.createMatch(callerId, needVenueRequest(List.of(venueY, venueZ), earliest));

        ArgumentCaptor<Match> saved = ArgumentCaptor.forClass(Match.class);
        var order = inOrder(teamGuard, sportRepository, venueSelectionService, matchRepository, venueOptionRepository,
                timeOptionRepository);
        order.verify(teamGuard).requireActiveCaptain(teamId, callerId);
        order.verify(sportRepository).findById(sportId);
        order.verify(venueSelectionService).requireSelectable(List.of(venueY, venueZ));
        order.verify(matchRepository).save(saved.capture());
        order.verify(venueOptionRepository).insertAll(matchId, new UUID[]{venueY, venueZ}, NOW);
        order.verify(timeOptionRepository).insertAllReturning(matchId,
                new LocalDateTime[]{earliest.plusHours(2), earliest},
                new LocalDateTime[]{earliest.plusHours(4), earliest.plusHours(2)}, NOW);
        Match match = saved.getValue();
        assertThat(match.getType()).isEqualTo(Match.Type.NEED_VENUE);
        assertThat(match.getStatus()).isEqualTo(Match.Status.OPEN);
        assertThat(match.getVenueId()).isNull();
        assertThat(match.getMatchStartTime()).isEqualTo(earliest);
        assertThat(match.getMatchEndTime()).isEqualTo(earliest.plusHours(2));
        assertThat(match.getExpiresAt()).isEqualTo(earliest.minusHours(5));
        assertThat(match.getAgeNote()).isEqualTo("Giao hữu");
        verify(venueSelectionService, never()).markSelected(any(), any());
        verify(matchRepository, never()).findDetailById(any(), any());
        verifyNoInteractions(publisher, costItemRepository);
        assertThat(response.matchId()).isEqualTo(matchId);
        assertThat(response.type()).isEqualTo("NEED_VENUE");
        assertThat(response.venue()).isNull();
        assertThat(response.sportName()).isEqualTo("Bóng đá");
        assertThat(response.authorTeam().teamName()).isEqualTo("Team Alpha");
        assertThat(response.venueOptions()).extracting("venueId").containsExactly(venueY, venueZ);
        assertThat(response.timeOptions()).extracting("timeOptionId").containsExactly(earlyOption, lateOption);
    }

    @Test
    void createNeedVenueChecksEveryVenueOptionBeforeSaving() {
        UUID venueY = UUID.randomUUID();
        when(sportRepository.findById(sportId)).thenReturn(Optional.of(Sport.builder().isActive(true).build()));
        when(venueSelectionService.requireSelectable(List.of(venueY))).thenThrow(new BusinessException(ErrorCode.VENUE_002));

        assertError(() -> service.createMatch(callerId, needVenueRequest(List.of(venueY), NOW.plusDays(1))),
                ErrorCode.VENUE_002);
        verifyNoInteractions(matchRepository, venueOptionRepository, timeOptionRepository);
    }

    @Test
    void createChecksTeamBeforeSportBeforeVenue() {
        when(teamGuard.requireActiveCaptain(teamId, callerId)).thenThrow(new BusinessException(ErrorCode.TEAM_015));
        assertError(() -> service.createMatch(callerId, createRequest(null)), ErrorCode.TEAM_015);
        verifyNoInteractions(sportRepository, venueSelectionService);
    }

    @Test
    void createRejectsMissingOrDisabledSport() {
        when(sportRepository.findById(sportId)).thenReturn(Optional.empty());
        assertError(() -> service.createMatch(callerId, createRequest(null)), ErrorCode.SPORT_001);

        when(sportRepository.findById(sportId)).thenReturn(Optional.of(Sport.builder().isActive(false).build()));
        assertError(() -> service.createMatch(callerId, createRequest(null)), ErrorCode.SPORT_002);
        verifyNoInteractions(venueSelectionService);
    }

    @Test
    void createSavesOpenMatchMarksVenueSelectedThenReadsDetail() {
        when(sportRepository.findById(sportId)).thenReturn(Optional.of(Sport.builder().isActive(true).build()));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(matchRepository.findDetailById(any(), eq(NOW))).thenReturn(Optional.of(detailView(UUID.randomUUID(), "HAS_VENUE")));

        MatchResponse response = service.createMatch(callerId, createRequest(null));

        ArgumentCaptor<Match> saved = ArgumentCaptor.forClass(Match.class);
        var order = inOrder(teamGuard, sportRepository, venueSelectionService, matchRepository);
        order.verify(teamGuard).requireActiveCaptain(teamId, callerId);
        order.verify(sportRepository).findById(sportId);
        order.verify(venueSelectionService).requireSelectable(List.of(venueId));
        order.verify(matchRepository).save(saved.capture());
        order.verify(venueSelectionService).markSelected(List.of(venueId), NOW);
        order.verify(matchRepository).findDetailById(any(), eq(NOW));
        Match match = saved.getValue();
        assertThat(match.getType()).isEqualTo(Match.Type.HAS_VENUE);
        assertThat(match.getStatus()).isEqualTo(Match.Status.OPEN);
        assertThat(match.getFormat()).isEqualTo(Match.Format.SEVEN_A_SIDE);
        assertThat(match.getSkillLevels()).isEmpty();
        assertThat(match.getAgeNote()).isEqualTo("U23");
        assertThat(match.getMatchNature()).isNull();
        assertThat(response.venueOptions()).isEmpty();
        verify(costItemRepository, never()).findByMatchIdOrderByDisplayOrderAsc(any());
    }

    @Test
    void searchWithoutCoordinatesUsesOpenSearch() {
        Page<MatchSummaryView> empty = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(matchRepository.searchOpen("HAS_VENUE", sportId, "FIVE_A_SIDE", "Chill", NOW, NOW.plusDays(1), NOW,
                PageRequest.of(0, 20))).thenReturn(empty);

        var page = service.searchMatches(new SearchMatchRequest("HAS_VENUE", sportId, "FIVE_A_SIDE", "Chill", NOW,
                NOW.plusDays(1), null, null, null, null, null));

        assertThat(page.data()).isEmpty();
        verify(matchRepository, never()).searchOpenNearby(any(), any(), any(), any(), any(), any(), any(), anyDouble(),
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any());
    }

    @Test
    void searchWithCoordinatesUsesBoundingBox() {
        GeoBoundingBox box = GeoBoundingBox.around(10.78, 106.7, 5);
        when(matchRepository.searchOpenNearby(null, null, null, null, null, null, NOW, 10.78, 106.7, 5.0, box.minLat(),
                box.maxLat(), box.minLng(), box.maxLng(), PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        service.searchMatches(new SearchMatchRequest(null, null, null, null, null, null, new BigDecimal("10.78"),
                new BigDecimal("106.7"), new BigDecimal("5"), null, null));

        verify(matchRepository).searchOpenNearby(null, null, null, null, null, null, NOW, 10.78, 106.7, 5.0,
                box.minLat(), box.maxLat(), box.minLng(), box.maxLng(), PageRequest.of(0, 20));
    }

    @Test
    void getMatchReadsDetailThenCustomItemsOfFlowA() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findDetailById(matchId, NOW)).thenReturn(Optional.empty());
        assertError(() -> service.getMatch(matchId), ErrorCode.MATCH_001);

        when(matchRepository.findDetailById(matchId, NOW)).thenReturn(Optional.of(detailView(matchId, "HAS_VENUE")));
        when(costItemRepository.findByMatchIdOrderByDisplayOrderAsc(matchId)).thenReturn(List.of(
                CostCustomItem.builder().id(UUID.randomUUID()).itemName("Nước").amount(5L).displayOrder(1).build()));

        assertThat(service.getMatch(matchId).costAgreement().customItems()).hasSize(1);
        verifyNoInteractions(venueOptionRepository);
    }

    MatchOptionRowView timeRow(LocalDateTime startsAt) {
        Map<String, Object> row = new HashMap<>();
        row.put("kind", "TIME");
        row.put("timeOptionId", UUID.randomUUID());
        row.put("startsAt", startsAt);
        row.put("endsAt", startsAt.plusHours(2));
        return projections.createProjection(MatchOptionRowView.class, row);
    }

    @Test
    void getMatchOfOpenNeedVenueReadsOptionsButNoCustomItems() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findDetailById(matchId, NOW)).thenReturn(Optional.of(detailView(matchId, "NEED_VENUE")));
        when(venueOptionRepository.findOptionRows(matchId)).thenReturn(List.of(timeRow(NOW.plusDays(1))));

        MatchResponse response = service.getMatch(matchId);

        assertThat(response.timeOptions()).hasSize(1);
        verify(costItemRepository, never()).findByMatchIdOrderByDisplayOrderAsc(any());
    }

    @Test
    void getMatchOfConfirmedNeedVenueReadsCopiedItemsAndOptions() {
        UUID matchId = UUID.randomUUID();
        Map<String, Object> row = new HashMap<>();
        row.put("matchId", matchId);
        row.put("type", "NEED_VENUE");
        row.put("effectiveStatus", "CONFIRMED");
        row.put("skillLevels", "[]");
        row.put("confirmedPairingId", UUID.randomUUID());
        when(matchRepository.findDetailById(matchId, NOW))
                .thenReturn(Optional.of(projections.createProjection(MatchDetailView.class, row)));
        when(costItemRepository.findByMatchIdOrderByDisplayOrderAsc(matchId)).thenReturn(List.of(
                CostCustomItem.builder().id(UUID.randomUUID()).itemName("Nước").amount(5L).displayOrder(1).build()));
        when(venueOptionRepository.findOptionRows(matchId)).thenReturn(List.of(timeRow(NOW.plusDays(1))));

        MatchResponse response = service.getMatch(matchId);

        assertThat(response.costAgreement().customItems()).hasSize(1);
        assertThat(response.timeOptions()).hasSize(1);
    }

    @Test
    void listTeamMatchesRequiresExistingTeam() {
        when(teamRepository.existsById(teamId)).thenReturn(false);
        assertError(() -> service.listTeamMatches(teamId, MatchStatusFilter.ALL, MatchRoleFilter.ALL, 1, 20),
                ErrorCode.TEAM_001);

        when(teamRepository.existsById(teamId)).thenReturn(true);
        when(matchRepository.findTeamMatches(teamId, "AUTHOR", "CONFIRMED", NOW, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        assertThat(service.listTeamMatches(teamId, MatchStatusFilter.CONFIRMED, MatchRoleFilter.AUTHOR, 1, 20).data())
                .isEmpty();
    }

    // ---- Lưu thoả thuận chi phí (2.4) ----

    Match openMatch(UUID matchId) {
        return Match.builder().id(matchId).type(Match.Type.HAS_VENUE).status(Match.Status.OPEN).authorTeamId(teamId)
                .matchStartTime(NOW.plusDays(1)).expiresAt(NOW.plusHours(5)).build();
    }

    UpdateCostAgreementRequest costRequest(List<CostCustomItemRequest> items) {
        return new UpdateCostAgreementRequest("BOTH", "FIFTY_FIFTY", 600_000L, 100_000L, items);
    }

    @Test
    void costAgreementReplacesItemsInOneInsertAndReadsBackIds() {
        UUID matchId = UUID.randomUUID();
        Match match = openMatch(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        CostCustomItem saved = CostCustomItem.builder().id(UUID.randomUUID()).itemName("Trọng tài").amount(200_000L)
                .displayOrder(1).build();
        when(costItemRepository.findByMatchIdOrderByDisplayOrderAsc(matchId)).thenReturn(List.of(saved));

        CostAgreementResponse response = service.updateCostAgreement(matchId, callerId,
                costRequest(List.of(new CostCustomItemRequest(" Trọng tài ", 200_000L))));

        verify(teamGuard).requireActiveCaptain(teamId, callerId);
        assertThat(match.getCostType()).isEqualTo(Match.CostType.BOTH);
        assertThat(match.getCostSplit()).isEqualTo(Match.CostSplit.FIFTY_FIFTY);
        assertThat(match.getUpdatedAt()).isEqualTo(NOW);
        var order = inOrder(costItemRepository);
        order.verify(costItemRepository).deleteByMatchIdBulk(matchId);
        order.verify(costItemRepository).insertAll(matchId, new String[]{"Trọng tài"}, new Long[]{200_000L}, NOW);
        order.verify(costItemRepository).findByMatchIdOrderByDisplayOrderAsc(matchId);
        assertThat(response.customItems()).extracting(CostCustomItemResponse::itemId).containsExactly(saved.getId());
        assertThat(response.costType()).isEqualTo("BOTH");
    }

    @Test
    void emptyCostItemsSkipInsertAndRead() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch(matchId)));

        CostAgreementResponse response = service.updateCostAgreement(matchId, callerId,
                new UpdateCostAgreementRequest(null, null, null, null, null));

        verify(costItemRepository).deleteByMatchIdBulk(matchId);
        verify(costItemRepository, never()).insertAll(any(), any(), any(), any());
        verify(costItemRepository, never()).findByMatchIdOrderByDisplayOrderAsc(any());
        assertThat(response.customItems()).isEmpty();
        assertThat(response.costType()).isNull();
    }

    @Test
    void costAgreementOnExpiredOpenMatchCommitsExpiryThenThrowsOutsideRetrier() {
        UUID matchId = UUID.randomUUID();
        Match match = openMatch(matchId);
        match.setExpiresAt(NOW);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertError(() -> service.updateCostAgreement(matchId, callerId, costRequest(List.of())), ErrorCode.MATCH_004);
        verify(matchExpiry).expireIfDue(matchId, teamId, Match.Type.HAS_VENUE, NOW);
        verify(costItemRepository, never()).deleteByMatchIdBulk(any());
        assertThat(match.getCostType()).isNull();
    }

    @Test
    void costAgreementGuardsStatusAndFlowB() {
        UUID matchId = UUID.randomUUID();
        Match match = openMatch(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        match.setStatus(Match.Status.CONFIRMED);
        assertError(() -> service.updateCostAgreement(matchId, callerId, costRequest(List.of())), ErrorCode.MATCH_003);
        match.setStatus(Match.Status.CANCELLED);
        assertError(() -> service.updateCostAgreement(matchId, callerId, costRequest(List.of())), ErrorCode.MATCH_005);
        match.setType(Match.Type.NEED_VENUE);
        assertError(() -> service.updateCostAgreement(matchId, callerId, costRequest(List.of())), ErrorCode.MATCH_016);
        verify(costItemRepository, never()).deleteByMatchIdBulk(any());
    }

    // ---- Huỷ kèo (2.5) ----

    Match confirmedMatch(UUID matchId, UUID opponentTeamId) {
        Match match = openMatch(matchId);
        match.setStatus(Match.Status.CONFIRMED);
        match.setConfirmedOpponentTeamId(opponentTeamId);
        return match;
    }

    @Test
    void cancelOpenWithoutPendingRequestsCancelsWithoutPenalty() {
        UUID matchId = UUID.randomUUID();
        Match match = openMatch(matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(requestRepository.existsByMatchIdAndStatus(matchId, MatchRequest.Status.PENDING)).thenReturn(false);

        MatchCancellationResponse response = service.cancelMatch(matchId, callerId,
                new CancelMatchRequest(teamId, "OPEN", "  "));

        verify(teamGuard).requireCaptainAllowInactive(teamId, callerId);
        assertThat(match.getStatus()).isEqualTo(Match.Status.CANCELLED);
        assertThat(response.reliabilityPenalty()).isZero();
        assertThat(response.cancelReason()).isNull();
        assertThat(response.cancelledAt()).isEqualTo(NOW);
        verify(publisher).publishEvent(new MatchCancelledEvent(matchId, teamId, null, Match.Status.OPEN,
                MatchCancelledEvent.Cause.CANCEL, null, NOW));
        verifyNoInteractions(matchCancellation);
    }

    @Test
    void cancelOpenNeedVenueSkipsPendingCheckAndSupersedesNegotiatingPairings() {
        UUID matchId = UUID.randomUUID();
        Match match = openMatch(matchId);
        match.setType(Match.Type.NEED_VENUE);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        MatchCancellationResponse response = service.cancelMatch(matchId, callerId,
                new CancelMatchRequest(teamId, "OPEN", " Đổi lịch "));

        assertThat(match.getStatus()).isEqualTo(Match.Status.CANCELLED);
        assertThat(response.reliabilityPenalty()).isZero();
        assertThat(response.cancelReason()).isEqualTo("Đổi lịch");
        verify(pairingRepository).supersedeNegotiating(matchId, NOW);
        verifyNoInteractions(requestRepository, matchCancellation);
        verify(publisher).publishEvent(new MatchCancelledEvent(matchId, teamId, null, Match.Status.OPEN,
                MatchCancelledEvent.Cause.CANCEL, null, NOW));
    }

    @Test
    void cancelOpenHasVenueNeverTouchesPairings() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch(matchId)));

        service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "OPEN", null));

        verifyNoInteractions(pairingRepository);
    }

    @Test
    void cancelOpenWithPendingRequestIsMatch010() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(openMatch(matchId)));
        when(requestRepository.existsByMatchIdAndStatus(matchId, MatchRequest.Status.PENDING)).thenReturn(true);

        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "OPEN", null)),
                ErrorCode.MATCH_010);
    }

    @Test
    void cancelComparesStatusWithClientExpectedStatusBeforeReason() {
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(confirmedMatch(matchId, UUID.randomUUID())));

        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "OPEN", null)),
                ErrorCode.MATCH_015);
        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "CONFIRMED", " ")),
                ErrorCode.MATCH_011);
        verifyNoInteractions(matchCancellation, publisher);
    }

    @Test
    void cancelByUnrelatedTeamIsMatch009AndFinishedMatchIsMatch005() {
        UUID matchId = UUID.randomUUID();
        Match match = confirmedMatch(matchId, UUID.randomUUID());
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertError(() -> service.cancelMatch(matchId, callerId,
                new CancelMatchRequest(UUID.randomUUID(), "CONFIRMED", "x")), ErrorCode.MATCH_009);
        match.setStatus(Match.Status.ARCHIVED);
        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "CONFIRMED", "x")),
                ErrorCode.MATCH_005);
        match.setStatus(Match.Status.EXPIRED);
        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "OPEN", "x")),
                ErrorCode.MATCH_004);
    }

    @Test
    void cancelConfirmedAfterStartIsMatch012() {
        UUID matchId = UUID.randomUUID();
        Match match = confirmedMatch(matchId, UUID.randomUUID());
        match.setMatchStartTime(NOW);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "CONFIRMED", "Bận")),
                ErrorCode.MATCH_012);
    }

    @Test
    void cancelConfirmedDelegatesToSharedCancellationByOpponent() {
        UUID matchId = UUID.randomUUID();
        UUID opponentTeamId = UUID.randomUUID();
        Match match = confirmedMatch(matchId, opponentTeamId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchCancellation.cancelConfirmed(match, opponentTeamId, "Thiếu người", MatchCancelledEvent.Cause.CANCEL,
                NOW)).thenReturn(new CancellationResult(-2));

        MatchCancellationResponse response = service.cancelMatch(matchId, callerId,
                new CancelMatchRequest(opponentTeamId, "CONFIRMED", " Thiếu người "));

        verify(teamGuard).requireCaptainAllowInactive(opponentTeamId, callerId);
        assertThat(response.reliabilityPenalty()).isEqualTo(-2);
    }

    @Test
    void cancelOnOpenMatchPastExpiryCommitsExpiryAndReturnsMatch004() {
        UUID matchId = UUID.randomUUID();
        Match match = openMatch(matchId);
        match.setExpiresAt(NOW.minusMinutes(1));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        assertError(() -> service.cancelMatch(matchId, callerId, new CancelMatchRequest(teamId, "OPEN", null)),
                ErrorCode.MATCH_004);
        verify(matchExpiry).expireIfDue(matchId, teamId, Match.Type.HAS_VENUE, NOW);
        assertThat(match.getStatus()).isEqualTo(Match.Status.OPEN);
    }
}
