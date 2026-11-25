package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.ConfirmPairingRequest;
import com.phananh.arenaflow.application.dto.request.CostCustomItemRequest;
import com.phananh.arenaflow.application.dto.request.CreatePairingRequest;
import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import com.phananh.arenaflow.application.dto.request.UpdateProposalRequest;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PairingConfirmationResponse;
import com.phananh.arenaflow.application.dto.response.PairingCostUpdateResponse;
import com.phananh.arenaflow.application.dto.response.PairingResponse;
import com.phananh.arenaflow.application.dto.response.PairingSummaryResponse;
import com.phananh.arenaflow.application.dto.response.ProposalUpdateResponse;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.application.event.match.PairingCreatedEvent;
import com.phananh.arenaflow.application.mapper.PairingMapper;
import com.phananh.arenaflow.application.service.impl.PairingServiceImpl;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchGuard;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.TeamGuard;
import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.PairingChange;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.application.support.match.PairingGuard;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.match.CostCustomItem;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.projection.ConfirmSideResult;
import com.phananh.arenaflow.domain.projection.MatchAuthorAccessView;
import com.phananh.arenaflow.domain.projection.MatchAvailabilityView;
import com.phananh.arenaflow.domain.projection.PairingContextView;
import com.phananh.arenaflow.domain.projection.PairingDetailView;
import com.phananh.arenaflow.domain.projection.PairingSummaryView;
import com.phananh.arenaflow.domain.projection.ProposalCandidateView;
import com.phananh.arenaflow.domain.projection.TeamCaptainContextView;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import com.phananh.arenaflow.domain.repository.CostCustomItemRepository;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.PairingRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.phananh.arenaflow.application.support.DataIntegrityViolationFixtures.violation;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PairingServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 15, 0);
    private static final UUID CALLER = UUID.randomUUID();
    private static final UUID MATCH_ID = UUID.randomUUID();
    private static final UUID PAIRING_ID = UUID.randomUUID();
    private static final UUID AUTHOR_TEAM = UUID.randomUUID();
    private static final UUID INTERESTED_TEAM = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID VENUE_ID = UUID.randomUUID();
    private static final UUID TIME_OPTION_ID = UUID.randomUUID();

    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final PairingRepository pairingRepository = mock(PairingRepository.class);
    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final CostCustomItemRepository costCustomItemRepository = mock(CostCustomItemRepository.class);
    private final ConversationRepository conversationRepository = mock(ConversationRepository.class);
    private final MatchGuard matchGuard = mock(MatchGuard.class);
    private final TeamGuard teamGuard = mock(TeamGuard.class);
    private final MatchExpiry matchExpiry = mock(MatchExpiry.class);
    private final VenueSelectionService venueSelectionService = mock(VenueSelectionService.class);
    private final ChatRealtimeGateway realtimeGateway = mock(ChatRealtimeGateway.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private PairingService service;

    @BeforeEach
    void setUp() {
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        Clock clock = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        service = new PairingServiceImpl(pairingRepository, matchRepository, costCustomItemRepository,
                conversationRepository, matchGuard, teamGuard, new PairingGuard(), matchExpiry, venueSelectionService,
                new ConcurrencyConflictRetrier(transactionTemplate), Mappers.getMapper(PairingMapper.class),
                new PageRequestFactory(), realtimeGateway, eventPublisher, clock);
    }

    @Nested
    class CreatePairing {

        private Match match;
        private final CreatePairingRequest request = new CreatePairingRequest(INTERESTED_TEAM);

        @BeforeEach
        void openNeedVenueMatch() {
            match = Match.builder().id(MATCH_ID).type(Match.Type.NEED_VENUE).status(Match.Status.OPEN)
                    .authorTeamId(AUTHOR_TEAM).expiresAt(NOW.plusDays(1)).build();
            when(matchGuard.findMatch(MATCH_ID)).thenReturn(match);
            when(teamGuard.requireActiveCaptain(INTERESTED_TEAM, CALLER)).thenReturn(captainContext());
            when(matchRepository.findAvailability(MATCH_ID, INTERESTED_TEAM)).thenReturn(availability("ACTIVE", true, null));
            when(pairingRepository.saveAndFlush(any(Pairing.class))).thenAnswer(invocation -> {
                Pairing pairing = invocation.getArgument(0);
                pairing.setId(PAIRING_ID);
                return pairing;
            });
            when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
                Conversation conversation = invocation.getArgument(0);
                conversation.setId(CONVERSATION_ID);
                return conversation;
            });
        }

        @Test
        void createsNegotiationConversationBumpsMatchVersionAndPublishesEvent() {
            PairingResponse response = service.createPairing(MATCH_ID, CALLER, request);

            ArgumentCaptor<Pairing> pairing = ArgumentCaptor.forClass(Pairing.class);
            verify(pairingRepository).saveAndFlush(pairing.capture());
            assertThat(pairing.getValue().getStatus()).isEqualTo(Pairing.Status.NEGOTIATING);
            assertThat(pairing.getValue().getRevision()).isZero();
            assertThat(pairing.getValue().getInterestedTeamId()).isEqualTo(INTERESTED_TEAM);
            ArgumentCaptor<Conversation> conversation = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationRepository).save(conversation.capture());
            assertThat(conversation.getValue().getContextType()).isEqualTo(Conversation.ContextType.TEAM_MATCH);
            assertThat(conversation.getValue().getMatchId()).isEqualTo(MATCH_ID);
            assertThat(conversation.getValue().getPartyAType()).isEqualTo(Conversation.PartyType.TEAM);
            assertThat(conversation.getValue().getPartyAId()).isEqualTo(AUTHOR_TEAM);
            assertThat(conversation.getValue().getPartyBType()).isEqualTo(Conversation.PartyType.TEAM);
            assertThat(conversation.getValue().getPartyBId()).isEqualTo(INTERESTED_TEAM);
            verify(matchRepository).forceIncrementVersion(match);
            ArgumentCaptor<PairingCreatedEvent> event = ArgumentCaptor.forClass(PairingCreatedEvent.class);
            verify(eventPublisher).publishEvent(event.capture());
            assertThat(event.getValue()).isEqualTo(new PairingCreatedEvent(PAIRING_ID, MATCH_ID, INTERESTED_TEAM,
                    AUTHOR_TEAM, NOW));
            assertThat(response.pairingId()).isEqualTo(PAIRING_ID);
            assertThat(response.conversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(response.status()).isEqualTo("NEGOTIATING");
            assertThat(response.authorTeam().teamName()).isEqualTo("Alpha");
            assertThat(response.interestedTeam().captainName()).isEqualTo("Nam");
        }

        @Test
        void reusesExistingConversationOfMatchAndTeam() {
            UUID existing = UUID.randomUUID();
            when(matchRepository.findAvailability(MATCH_ID, INTERESTED_TEAM))
                    .thenReturn(availability("ACTIVE", true, existing));

            PairingResponse response = service.createPairing(MATCH_ID, CALLER, request);

            verify(conversationRepository, never()).save(any());
            assertThat(response.conversationId()).isEqualTo(existing);
        }

        @Test
        void rejectsOwnMatchBeforeTypeCheck() {
            when(teamGuard.requireActiveCaptain(AUTHOR_TEAM, CALLER)).thenReturn(captainContext());
            match.setType(Match.Type.HAS_VENUE);

            assertError(() -> service.createPairing(MATCH_ID, CALLER, new CreatePairingRequest(AUTHOR_TEAM)),
                    ErrorCode.PAIRING_004);
            verify(pairingRepository, never()).saveAndFlush(any());
        }

        @Test
        void rejectsFlowAMatch() {
            match.setType(Match.Type.HAS_VENUE);

            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.MATCH_016);
            verify(matchRepository, never()).findAvailability(any(), any());
        }

        @Test
        void teamGuardErrorsComeBeforeMatchState() {
            doThrow(new BusinessException(ErrorCode.TEAM_015)).when(teamGuard).requireActiveCaptain(INTERESTED_TEAM, CALLER);

            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.TEAM_015);
            verify(matchGuard, never()).requireOpen(any());
        }

        @Test
        void overdueOpenMatchIsExpiredAndCommittedThenReportsMatch004() {
            when(matchGuard.isExpiryDue(Match.Status.OPEN, match.getExpiresAt(), NOW)).thenReturn(true);

            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.MATCH_004);
            verify(matchExpiry).expireIfDue(MATCH_ID, AUTHOR_TEAM, Match.Type.NEED_VENUE, NOW);
            verify(matchGuard, never()).requireOpen(any());
            verify(pairingRepository, never()).saveAndFlush(any());
        }

        @Test
        void matchStateErrorsFromGuard() {
            doThrow(new BusinessException(ErrorCode.MATCH_003)).when(matchGuard).requireOpen(match);

            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.MATCH_003);
            verify(matchRepository, never()).findAvailability(any(), any());
        }

        @Test
        void inactiveAuthorTeamThenDisabledSport() {
            when(matchRepository.findAvailability(MATCH_ID, INTERESTED_TEAM)).thenReturn(availability("INACTIVE", false, null));
            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.MATCH_013);

            when(matchRepository.findAvailability(MATCH_ID, INTERESTED_TEAM)).thenReturn(availability("ACTIVE", false, null));
            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.SPORT_002);
            verify(pairingRepository, never()).saveAndFlush(any());
        }

        @Test
        void activePairingUniqueViolationBecomesPairing003() {
            when(pairingRepository.saveAndFlush(any(Pairing.class))).thenThrow(violation("idx_pairing_active"));

            assertError(() -> service.createPairing(MATCH_ID, CALLER, request), ErrorCode.PAIRING_003);
            verify(conversationRepository, never()).save(any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void otherIntegrityViolationIsRethrown() {
            DataIntegrityViolationException other = violation("some_other_constraint");
            when(pairingRepository.saveAndFlush(any(Pairing.class))).thenThrow(other);

            assertThatThrownBy(() -> service.createPairing(MATCH_ID, CALLER, request)).isSameAs(other);
        }
    }

    @Nested
    class Reads {

        @Test
        void listMatchPairingsRequiresAuthorCaptainAndFlowBMatch() {
            when(matchRepository.findAuthorAccess(MATCH_ID, CALLER)).thenReturn(Optional.empty());
            assertError(() -> service.listMatchPairings(MATCH_ID, CALLER, PairingStatusFilter.ALL, 1, 20),
                    ErrorCode.MATCH_001);

            when(matchRepository.findAuthorAccess(MATCH_ID, CALLER)).thenReturn(Optional.of(access("DISBANDED", false,
                    "NEED_VENUE")));
            assertError(() -> service.listMatchPairings(MATCH_ID, CALLER, PairingStatusFilter.ALL, 1, 20),
                    ErrorCode.TEAM_016);

            when(matchRepository.findAuthorAccess(MATCH_ID, CALLER)).thenReturn(Optional.of(access("ACTIVE", false,
                    "NEED_VENUE")));
            assertError(() -> service.listMatchPairings(MATCH_ID, CALLER, PairingStatusFilter.ALL, 1, 20),
                    ErrorCode.TEAM_002);

            when(matchRepository.findAuthorAccess(MATCH_ID, CALLER)).thenReturn(Optional.of(access("INACTIVE", true,
                    "HAS_VENUE")));
            assertError(() -> service.listMatchPairings(MATCH_ID, CALLER, PairingStatusFilter.ALL, 1, 20),
                    ErrorCode.MATCH_016);
            verify(pairingRepository, never()).findByMatch(any(), any(), any(), any());
        }

        @Test
        void listMatchPairingsPassesFilterAndNow() {
            when(matchRepository.findAuthorAccess(MATCH_ID, CALLER)).thenReturn(Optional.of(access("INACTIVE", true,
                    "NEED_VENUE")));
            PairingSummaryView row = projection(PairingSummaryView.class, Map.of("pairingId", PAIRING_ID,
                    "effectiveStatus", "NEGOTIATING", "revision", 1));
            when(pairingRepository.findByMatch(MATCH_ID, "NEGOTIATING", NOW, PageRequest.of(1, 10)))
                    .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(1, 10), 11));

            PageResponse<PairingSummaryResponse> page = service.listMatchPairings(MATCH_ID, CALLER,
                    PairingStatusFilter.NEGOTIATING, 2, 10);

            assertThat(page.data()).extracting(PairingSummaryResponse::pairingId).containsExactly(PAIRING_ID);
            assertThat(page.pagination().totalItems()).isEqualTo(11);
        }

        @Test
        void listTeamPairingsAllowsInactiveCaptainAndPassesRoleAndStatus() {
            when(pairingRepository.findByTeam(INTERESTED_TEAM, null, null, NOW, PageRequest.of(0, 20)))
                    .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            service.listTeamPairings(INTERESTED_TEAM, CALLER, PairingRoleFilter.ALL, PairingStatusFilter.ALL, 1, 20);

            verify(teamGuard).requireCaptainAllowInactive(INTERESTED_TEAM, CALLER);
            verify(pairingRepository).findByTeam(INTERESTED_TEAM, null, null, NOW, PageRequest.of(0, 20));
        }

        @Test
        void listTeamPairingsStopsOnTeamGuardError() {
            doThrow(new BusinessException(ErrorCode.TEAM_002)).when(teamGuard)
                    .requireCaptainAllowInactive(INTERESTED_TEAM, CALLER);

            assertError(() -> service.listTeamPairings(INTERESTED_TEAM, CALLER, PairingRoleFilter.AUTHOR,
                    PairingStatusFilter.ALL, 1, 20), ErrorCode.TEAM_002);
            verifyNoInteractions(pairingRepository);
        }

        @Test
        void getPairingRequiresCaptainOfEitherTeamAndReadsItemsOfPairing() {
            when(pairingRepository.findDetailById(PAIRING_ID, CALLER, NOW)).thenReturn(Optional.empty());
            assertError(() -> service.getPairing(PAIRING_ID, CALLER), ErrorCode.PAIRING_001);

            when(pairingRepository.findDetailById(PAIRING_ID, CALLER, NOW)).thenReturn(Optional.of(detail(false, false)));
            assertError(() -> service.getPairing(PAIRING_ID, CALLER), ErrorCode.PAIRING_002);
            verify(costCustomItemRepository, never()).findByPairingIdOrderByDisplayOrderAsc(any());

            when(pairingRepository.findDetailById(PAIRING_ID, CALLER, NOW)).thenReturn(Optional.of(detail(false, true)));
            when(costCustomItemRepository.findByPairingIdOrderByDisplayOrderAsc(PAIRING_ID))
                    .thenReturn(List.of(CostCustomItem.builder().id(UUID.randomUUID()).itemName("Nước").amount(1L).build()));
            PairingResponse response = service.getPairing(PAIRING_ID, CALLER);

            assertThat(response.pairingId()).isEqualTo(PAIRING_ID);
            assertThat(response.costAgreement().customItems()).hasSize(1);
        }
    }

    @Nested
    class UpdateProposal {

        private final UpdateProposalRequest request = new UpdateProposalRequest(INTERESTED_TEAM, 1, VENUE_ID,
                TIME_OPTION_ID);

        @BeforeEach
        void stubValidCandidate() {
            when(pairingRepository.findProposalCandidate(MATCH_ID, VENUE_ID, TIME_OPTION_ID))
                    .thenReturn(Optional.of(candidate("ACTIVE")));
            when(pairingRepository.updateProposal(PAIRING_ID, VENUE_ID, TIME_OPTION_ID, 1, NOW)).thenReturn(1);
        }

        @Test
        void savesNewProposalResetsConfirmationsAndPushesFrame() {
            givenContext(Map.of("revision", 1, "authorConfirmedAt", NOW.minusHours(1)));

            ProposalUpdateResponse response = service.updateProposal(PAIRING_ID, CALLER, request);

            assertThat(response.revision()).isEqualTo(2);
            assertThat(response.authorConfirmedAt()).isNull();
            assertThat(response.interestedConfirmedAt()).isNull();
            assertThat(response.updatedAt()).isEqualTo(NOW);
            assertThat(response.proposal().venueName()).isEqualTo("Sân Y");
            assertFrame(Pairing.Status.NEGOTIATING, 2, PairingChange.PROPOSAL);
        }

        @Test
        void sameProposalWithCurrentRevisionWritesNothing() {
            givenContext(Map.of("revision", 1, "proposedVenueId", VENUE_ID, "proposedTimeOptionId", TIME_OPTION_ID,
                    "authorConfirmedAt", NOW.minusHours(1), "updatedAt", NOW.minusHours(2)));

            ProposalUpdateResponse response = service.updateProposal(PAIRING_ID, CALLER, request);

            assertThat(response.revision()).isEqualTo(1);
            assertThat(response.authorConfirmedAt()).isEqualTo(NOW.minusHours(1));
            assertThat(response.updatedAt()).isEqualTo(NOW.minusHours(2));
            verify(pairingRepository, never()).updateProposal(any(), any(), any(), anyInt(), any());
            verifyNoInteractions(realtimeGateway);
        }

        @Test
        void staleRevisionWinsOverSameProposal() {
            givenContext(Map.of("revision", 2, "proposedVenueId", VENUE_ID, "proposedTimeOptionId", TIME_OPTION_ID));

            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.PAIRING_006);
            verify(pairingRepository, never()).findProposalCandidate(any(), any(), any());
        }

        @Test
        void optionOutsideMatchThenInactiveVenue() {
            givenContext(Map.of("revision", 1));
            when(pairingRepository.findProposalCandidate(MATCH_ID, VENUE_ID, TIME_OPTION_ID)).thenReturn(Optional.empty());
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.PAIRING_008);

            when(pairingRepository.findProposalCandidate(MATCH_ID, VENUE_ID, TIME_OPTION_ID))
                    .thenReturn(Optional.of(candidate("INACTIVE")));
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.VENUE_002);
            verify(pairingRepository, never()).updateProposal(any(), any(), any(), anyInt(), any());
        }

        @Test
        void casMissRetriesAndReportsMatch014WhenItKeepsMissing() {
            givenContext(Map.of("revision", 1));
            when(pairingRepository.updateProposal(PAIRING_ID, VENUE_ID, TIME_OPTION_ID, 1, NOW)).thenReturn(0);

            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.MATCH_014);
            verify(pairingRepository, times(3)).findContext(PAIRING_ID, CALLER);
            verifyNoInteractions(realtimeGateway);
        }

        @Test
        void guardOrderNotFoundSideTeamThenExpiryThenStatus() {
            when(pairingRepository.findContext(PAIRING_ID, CALLER)).thenReturn(Optional.empty());
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.PAIRING_001);

            givenContext(Map.of("revision", 1));
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER,
                    new UpdateProposalRequest(UUID.randomUUID(), 1, VENUE_ID, TIME_OPTION_ID)), ErrorCode.PAIRING_002);

            givenContext(Map.of("revision", 1, "interestedTeamStatus", "INACTIVE", "status", "SUPERSEDED"));
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.TEAM_015);

            givenContext(Map.of("revision", 1, "callerInterestedCaptain", false));
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.TEAM_002);

            givenContext(Map.of("revision", 0, "status", "CONFIRMED"));
            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.PAIRING_005);
            verify(matchExpiry, never()).expireIfDue(any(), any(), any(), any());
        }

        @Test
        void overdueMatchExpiresAndReportsMatch004WithoutWriting() {
            givenContext(Map.of("revision", 1));
            when(matchGuard.isExpiryDue(Match.Status.OPEN, NOW.plusDays(1), NOW)).thenReturn(true);

            assertError(() -> service.updateProposal(PAIRING_ID, CALLER, request), ErrorCode.MATCH_004);
            verify(matchExpiry).expireIfDue(MATCH_ID, AUTHOR_TEAM, Match.Type.NEED_VENUE, NOW);
            verify(pairingRepository, never()).findProposalCandidate(any(), any(), any());
        }

        @Test
        void missingConversationIsBrokenInvariant() {
            Map<String, Object> values = new HashMap<>(Map.of("revision", 1));
            values.put("conversationId", null);
            givenContext(values);

            assertThatThrownBy(() -> service.updateProposal(PAIRING_ID, CALLER, request))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class UpdateCostAgreement {

        @Test
        void replacesCostAndItemsTrimsTextAndPushesFrame() {
            givenContext(Map.of("revision", 2));
            when(pairingRepository.updateCost(PAIRING_ID, "VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null, 200_000L,
                    "Đội chủ nhà", 2, NOW)).thenReturn(1);
            List<CostCustomItem> saved = List.of(CostCustomItem.builder().id(UUID.randomUUID()).itemName("Trọng tài")
                    .amount(200_000L).displayOrder(1).build());
            when(costCustomItemRepository.findByPairingIdOrderByDisplayOrderAsc(PAIRING_ID)).thenReturn(saved);

            PairingCostUpdateResponse response = service.updateCostAgreement(PAIRING_ID, CALLER,
                    new UpdatePairingCostAgreementRequest(INTERESTED_TEAM, 2, "VENUE_ONLY", "FIFTY_FIFTY", 600_000L,
                            null, 200_000L, "  Đội chủ nhà ", List.of(new CostCustomItemRequest(" Trọng tài ", 200_000L))));

            verify(costCustomItemRepository).deleteByPairingIdBulk(PAIRING_ID);
            verify(costCustomItemRepository).insertAllForPairing(PAIRING_ID, new String[] {"Trọng tài"},
                    new Long[] {200_000L}, NOW);
            assertThat(response.revision()).isEqualTo(3);
            assertThat(response.costAgreement().depositHolder()).isEqualTo("Đội chủ nhà");
            assertThat(response.costAgreement().customItems()).hasSize(1);
            assertThat(response.authorConfirmedAt()).isNull();
            assertThat(response.updatedAt()).isEqualTo(NOW);
            assertFrame(Pairing.Status.NEGOTIATING, 3, PairingChange.COST);
        }

        @Test
        void emptyItemsSkipInsertAndRereadBlankHolderBecomesNull() {
            givenContext(Map.of("revision", 0));
            when(pairingRepository.updateCost(PAIRING_ID, null, null, null, null, null, null, 0, NOW)).thenReturn(1);

            PairingCostUpdateResponse response = service.updateCostAgreement(PAIRING_ID, CALLER,
                    new UpdatePairingCostAgreementRequest(INTERESTED_TEAM, 0, null, null, null, null, null, " ", null));

            verify(costCustomItemRepository).deleteByPairingIdBulk(PAIRING_ID);
            verify(costCustomItemRepository, never()).insertAllForPairing(any(), any(), any(), any());
            verify(costCustomItemRepository, never()).findByPairingIdOrderByDisplayOrderAsc(any());
            assertThat(response.costAgreement().customItems()).isEmpty();
            assertThat(response.costAgreement().depositHolder()).isNull();
        }

        @Test
        void casMissDoesNotTouchItems() {
            givenContext(Map.of("revision", 0));
            when(pairingRepository.updateCost(any(), any(), any(), any(), any(), any(), any(), anyInt(), any()))
                    .thenReturn(0);

            assertError(() -> service.updateCostAgreement(PAIRING_ID, CALLER, new UpdatePairingCostAgreementRequest(
                    INTERESTED_TEAM, 0, null, null, null, null, null, null, null)), ErrorCode.MATCH_014);
            verify(costCustomItemRepository, never()).deleteByPairingIdBulk(any());
        }

        @Test
        void staleRevision() {
            givenContext(Map.of("revision", 4));

            assertError(() -> service.updateCostAgreement(PAIRING_ID, CALLER, new UpdatePairingCostAgreementRequest(
                    INTERESTED_TEAM, 3, null, null, null, null, null, null, null)), ErrorCode.PAIRING_006);
        }
    }

    @Nested
    class Confirm {

        private final ConfirmPairingRequest authorConfirms = new ConfirmPairingRequest(AUTHOR_TEAM, 3);

        @Test
        void firstSideRecordsConfirmationAndStaysNegotiating() {
            givenReadyContext(Map.of());
            when(pairingRepository.confirmSide(PAIRING_ID, "AUTHOR", 3, NOW))
                    .thenReturn(Optional.of(confirmSide(NOW, null)));

            PairingConfirmationResponse response = service.confirm(PAIRING_ID, CALLER, authorConfirms);

            assertThat(response.status()).isEqualTo("NEGOTIATING");
            assertThat(response.authorConfirmedAt()).isEqualTo(NOW);
            assertThat(response.interestedConfirmedAt()).isNull();
            assertThat(response.matchStatus()).isEqualTo("OPEN");
            assertFrame(Pairing.Status.NEGOTIATING, 3, PairingChange.CONFIRMATION);
            verify(matchRepository, never()).confirmPairing(any(), anyLong(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), any(), any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        void secondSideCompletesMatchSupersedesOthersCopiesItemsMarksVenueAndPublishesEvent() {
            LocalDateTime interestedAt = NOW.minusMinutes(5);
            givenReadyContext(Map.of("interestedConfirmedAt", interestedAt, "costType", "VENUE_ONLY",
                    "costSplit", "FIFTY_FIFTY", "costVenueAmount", 600_000L, "costDepositAmount", 200_000L,
                    "costDepositHolder", "Chủ nhà"));
            when(matchRepository.confirmPairing(MATCH_ID, 4L, INTERESTED_TEAM, VENUE_ID, NOW.plusDays(2),
                    NOW.plusDays(2).plusHours(2), "VENUE_ONLY", "FIFTY_FIFTY", 600_000L, null, 200_000L, "Chủ nhà", NOW))
                    .thenReturn(1);
            when(pairingRepository.finalizeConfirmation(MATCH_ID, PAIRING_ID, 3, NOW))
                    .thenReturn(List.of(PAIRING_ID, UUID.randomUUID()));

            PairingConfirmationResponse response = service.confirm(PAIRING_ID, CALLER, authorConfirms);

            verify(pairingRepository, never()).confirmSide(any(), any(), anyInt(), any());
            verify(costCustomItemRepository).copyPairingItemsToMatch(PAIRING_ID, MATCH_ID, NOW);
            verify(venueSelectionService).markSelected(List.of(VENUE_ID), NOW);
            verify(eventPublisher).publishEvent(new MatchConfirmedEvent(MATCH_ID, null, PAIRING_ID, AUTHOR_TEAM,
                    INTERESTED_TEAM, NOW));
            assertThat(response.status()).isEqualTo("CONFIRMED");
            assertThat(response.matchStatus()).isEqualTo("CONFIRMED");
            assertThat(response.authorConfirmedAt()).isEqualTo(NOW);
            assertThat(response.interestedConfirmedAt()).isEqualTo(interestedAt);
            assertThat(response.confirmedAt()).isEqualTo(NOW);
            assertFrame(Pairing.Status.CONFIRMED, 3, PairingChange.CONFIRMATION);
        }

        @Test
        void otherSideConfirmedConcurrentlyRollsBackAndCompletesOnRetry() {
            when(pairingRepository.findContext(PAIRING_ID, CALLER))
                    .thenReturn(Optional.of(readyContext(Map.of())))
                    .thenReturn(Optional.of(readyContext(Map.of("interestedConfirmedAt", NOW.minusSeconds(1)))));
            when(pairingRepository.confirmSide(PAIRING_ID, "AUTHOR", 3, NOW))
                    .thenReturn(Optional.of(confirmSide(NOW, NOW.minusSeconds(1))));
            when(matchRepository.confirmPairing(eq(MATCH_ID), eq(4L), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any(), eq(NOW))).thenReturn(1);
            when(pairingRepository.finalizeConfirmation(MATCH_ID, PAIRING_ID, 3, NOW)).thenReturn(List.of(PAIRING_ID));

            PairingConfirmationResponse response = service.confirm(PAIRING_ID, CALLER, authorConfirms);

            assertThat(response.status()).isEqualTo("CONFIRMED");
            verify(pairingRepository, times(1)).confirmSide(PAIRING_ID, "AUTHOR", 3, NOW);
            verify(realtimeGateway, times(1)).publishAfterCommit(eq(CONVERSATION_ID), any());
        }

        @Test
        void completionRetriesWhenMatchCasOrFinalizeMisses() {
            givenReadyContext(Map.of("interestedConfirmedAt", NOW.minusMinutes(1)));
            when(matchRepository.confirmPairing(any(), anyLong(), any(), any(), any(), any(), any(), any(), any(), any(),
                    any(), any(), any())).thenReturn(0, 1, 1);
            when(pairingRepository.finalizeConfirmation(MATCH_ID, PAIRING_ID, 3, NOW))
                    .thenReturn(List.of(UUID.randomUUID()));

            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.MATCH_014);
            verify(pairingRepository, times(2)).finalizeConfirmation(MATCH_ID, PAIRING_ID, 3, NOW);
            verify(costCustomItemRepository, never()).copyPairingItemsToMatch(any(), any(), any());
            verifyNoInteractions(eventPublisher, venueSelectionService, realtimeGateway);
        }

        @Test
        void ownSideAlreadyConfirmedReturnsCurrentStateWithoutWriting() {
            givenReadyContext(Map.of("authorConfirmedAt", NOW.minusMinutes(3)));

            PairingConfirmationResponse response = service.confirm(PAIRING_ID, CALLER, authorConfirms);

            assertThat(response.status()).isEqualTo("NEGOTIATING");
            assertThat(response.authorConfirmedAt()).isEqualTo(NOW.minusMinutes(3));
            verify(pairingRepository, never()).confirmSide(any(), any(), anyInt(), any());
            verifyNoInteractions(realtimeGateway);
        }

        @Test
        void checksAfterRevisionInOrder() {
            Map<String, Object> noProposal = new HashMap<>();
            noProposal.put("proposedVenueId", null);
            noProposal.put("proposedVenueStatus", "INACTIVE");
            givenReadyContext(noProposal);
            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.PAIRING_007);

            givenReadyContext(Map.of("proposedVenueStatus", "INACTIVE", "interestedTeamStatus", "INACTIVE"));
            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.VENUE_002);

            givenReadyContext(Map.of("interestedTeamStatus", "INACTIVE", "sportActive", false));
            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.MATCH_013);

            givenReadyContext(Map.of("sportActive", false, "authorConfirmedAt", NOW));
            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.SPORT_002);

            givenReadyContext(Map.of("revision", 2));
            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.PAIRING_006);
            verify(pairingRepository, never()).confirmSide(any(), any(), anyInt(), any());
        }

        @Test
        void inactiveOwnTeamCannotConfirm() {
            givenReadyContext(Map.of("authorTeamStatus", "INACTIVE"));

            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.TEAM_015);
        }

        @Test
        void overdueMatchExpiresBeforeStatusChecks() {
            givenReadyContext(Map.of("revision", 0));
            when(matchGuard.isExpiryDue(Match.Status.OPEN, NOW.plusDays(1), NOW)).thenReturn(true);

            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.MATCH_004);
            verify(matchExpiry).expireIfDue(MATCH_ID, AUTHOR_TEAM, Match.Type.NEED_VENUE, NOW);
        }

        @Test
        void confirmSideMissRetries() {
            givenReadyContext(Map.of());
            when(pairingRepository.confirmSide(PAIRING_ID, "AUTHOR", 3, NOW)).thenReturn(Optional.empty());

            assertError(() -> service.confirm(PAIRING_ID, CALLER, authorConfirms), ErrorCode.MATCH_014);
            verify(pairingRepository, times(3)).confirmSide(PAIRING_ID, "AUTHOR", 3, NOW);
        }

        private void givenReadyContext(Map<String, Object> overrides) {
            when(pairingRepository.findContext(PAIRING_ID, CALLER)).thenReturn(Optional.of(readyContext(overrides)));
        }

        private PairingContextView readyContext(Map<String, Object> overrides) {
            Map<String, Object> values = new HashMap<>(Map.of("revision", 3, "callerAuthorCaptain", true,
                    "proposedVenueId", VENUE_ID, "proposedTimeOptionId", TIME_OPTION_ID,
                    "proposedStartTime", NOW.plusDays(2), "proposedEndTime", NOW.plusDays(2).plusHours(2),
                    "proposedVenueStatus", "ACTIVE", "matchVersion", 4L));
            values.putAll(overrides);
            return context(values);
        }
    }

    private void givenContext(Map<String, Object> overrides) {
        when(pairingRepository.findContext(PAIRING_ID, CALLER)).thenReturn(Optional.of(context(overrides)));
    }

    /** Ngữ cảnh mặc định: pairing NEGOTIATING trên kèo B OPEN chưa quá hạn; người gọi là đội trưởng đội nhận kèo. */
    private PairingContextView context(Map<String, Object> overrides) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", PAIRING_ID);
        values.put("matchId", MATCH_ID);
        values.put("interestedTeamId", INTERESTED_TEAM);
        values.put("status", "NEGOTIATING");
        values.put("revision", 0);
        values.put("updatedAt", NOW.minusHours(1));
        values.put("authorTeamId", AUTHOR_TEAM);
        values.put("matchStatus", "OPEN");
        values.put("expiresAt", NOW.plusDays(1));
        values.put("matchType", "NEED_VENUE");
        values.put("matchVersion", 0L);
        values.put("authorTeamStatus", "ACTIVE");
        values.put("interestedTeamStatus", "ACTIVE");
        values.put("sportActive", true);
        values.put("conversationId", CONVERSATION_ID);
        values.put("callerAuthorCaptain", false);
        values.put("callerInterestedCaptain", true);
        values.putAll(overrides);
        return projection(PairingContextView.class, values);
    }

    private void assertFrame(Pairing.Status status, int revision, PairingChange change) {
        ArgumentCaptor<RealtimeFrame> frame = ArgumentCaptor.forClass(RealtimeFrame.class);
        verify(realtimeGateway).publishAfterCommit(eq(CONVERSATION_ID), frame.capture());
        assertThat(frame.getValue()).isInstanceOfSatisfying(RealtimeFrame.PairingUpdated.class, updated -> {
            assertThat(updated.pairingId()).isEqualTo(PAIRING_ID);
            assertThat(updated.status()).isEqualTo(status);
            assertThat(updated.revision()).isEqualTo(revision);
            assertThat(updated.change()).isEqualTo(change);
        });
    }

    private TeamCaptainContextView captainContext() {
        return projection(TeamCaptainContextView.class, Map.of("status", "ACTIVE", "captain", true, "name", "Beta",
                "reliabilityScore", 75, "userDisplayName", "Nam", "userDeleted", false));
    }

    private MatchAvailabilityView availability(String authorTeamStatus, boolean sportActive, UUID conversationId) {
        Map<String, Object> values = new HashMap<>(Map.of("authorTeamStatus", authorTeamStatus,
                "sportActive", sportActive, "authorTeamName", "Alpha", "authorTeamReliabilityScore", 80,
                "authorCaptainName", "Minh", "authorCaptainDeleted", false));
        values.put("existingConversationId", conversationId);
        return projection(MatchAvailabilityView.class, values);
    }

    private MatchAuthorAccessView access(String authorTeamStatus, boolean callerIsCaptain, String type) {
        return projection(MatchAuthorAccessView.class, Map.of("authorTeamId", AUTHOR_TEAM,
                "authorTeamStatus", authorTeamStatus, "callerIsCaptain", callerIsCaptain, "type", type));
    }

    private PairingDetailView detail(boolean callerAuthorCaptain, boolean callerInterestedCaptain) {
        return projection(PairingDetailView.class, Map.of("pairingId", PAIRING_ID, "matchId", MATCH_ID,
                "effectiveStatus", "NEGOTIATING", "revision", 0, "callerAuthorCaptain", callerAuthorCaptain,
                "callerInterestedCaptain", callerInterestedCaptain));
    }

    private ProposalCandidateView candidate(String venueStatus) {
        return projection(ProposalCandidateView.class, Map.of("venueId", VENUE_ID, "venueName", "Sân Y",
                "address", "10 Lê Lợi", "venueStatus", venueStatus, "timeOptionId", TIME_OPTION_ID,
                "startsAt", NOW.plusDays(2), "endsAt", NOW.plusDays(2).plusHours(2)));
    }

    private ConfirmSideResult confirmSide(LocalDateTime authorConfirmedAt, LocalDateTime interestedConfirmedAt) {
        Map<String, Object> values = new HashMap<>();
        values.put("authorConfirmedAt", authorConfirmedAt);
        values.put("interestedConfirmedAt", interestedConfirmedAt);
        return projection(ConfirmSideResult.class, values);
    }

    private <T> T projection(Class<T> type, Map<String, Object> values) {
        return projections.createProjection(type, values);
    }

    private static void assertError(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
