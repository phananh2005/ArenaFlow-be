package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.service.ReliabilityChange;
import com.phananh.arenaflow.application.service.ReliabilityReason;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Huỷ kèo đã ghép (huỷ CONFIRMED, rút ACCEPTED) của luồng A và luồng B. */
class MatchCancellationTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 22, 18, 0);
    private static final LocalDateTime NOW = START.minusHours(10);

    private final MatchRequestRepository requestRepository = mock(MatchRequestRepository.class);
    private final ReputationService reputationService = mock(ReputationService.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final MatchCancellation cancellation = new MatchCancellation(requestRepository, reputationService, publisher);
    private final UUID matchId = UUID.randomUUID();
    private final UUID authorTeamId = UUID.randomUUID();
    private final UUID opponentTeamId = UUID.randomUUID();

    private Match confirmedMatch() {
        return Match.builder().id(matchId).type(Match.Type.HAS_VENUE).status(Match.Status.CONFIRMED)
                .authorTeamId(authorTeamId).confirmedOpponentTeamId(opponentTeamId).matchStartTime(START).build();
    }

    @Test
    void authorCancelPenalisesAuthorOnlyAndLeavesAcceptedRequest() {
        Match match = confirmedMatch();

        CancellationResult result = cancellation.cancelConfirmed(match, authorTeamId, "Thiếu người",
                MatchCancelledEvent.Cause.CANCEL, NOW);

        assertThat(result.reliabilityPenalty()).isEqualTo(-5);
        assertThat(match.getStatus()).isEqualTo(Match.Status.CANCELLED);
        assertThat(match.getCancelledByTeamId()).isEqualTo(authorTeamId);
        assertThat(match.getCancelledAt()).isEqualTo(NOW);
        assertThat(match.getCancelReason()).isEqualTo("Thiếu người");
        verify(requestRepository, never()).withdrawAccepted(any(), any(), any(), any());
        verify(reputationService).adjustTeamReliability(authorTeamId,
                new ReliabilityChange(-5, ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, matchId, true));
        verify(publisher).publishEvent(new MatchCancelledEvent(matchId, authorTeamId, opponentTeamId,
                Match.Status.CONFIRMED, MatchCancelledEvent.Cause.CANCEL, null, NOW));
    }

    @Test
    void opponentWithdrawalWithdrawsAcceptedRequestAndPublishesSingleEvent() {
        Match match = confirmedMatch();
        UUID requestId = UUID.randomUUID();
        when(requestRepository.withdrawAccepted(matchId, opponentTeamId, "Không đủ người", NOW))
                .thenReturn(List.of(requestId));

        CancellationResult result = cancellation.cancelConfirmed(match, opponentTeamId, "Không đủ người",
                MatchCancelledEvent.Cause.REQUEST_WITHDRAWN, NOW);

        assertThat(result.reliabilityPenalty()).isEqualTo(-5);
        verify(reputationService).adjustTeamReliability(opponentTeamId,
                new ReliabilityChange(-5, ReliabilityReason.MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED, matchId, true));
        verify(publisher).publishEvent(new MatchCancelledEvent(matchId, opponentTeamId, authorTeamId,
                Match.Status.CONFIRMED, MatchCancelledEvent.Cause.REQUEST_WITHDRAWN, requestId, NOW));
    }

    @Test
    void needVenueOpponentCancelHasNoRequestAndPenalisesByConfirmedStart() {
        Match match = confirmedMatch();
        match.setType(Match.Type.NEED_VENUE);
        match.setMatchStartTime(NOW.plusHours(3));

        CancellationResult result = cancellation.cancelConfirmed(match, opponentTeamId, "Mưa to",
                MatchCancelledEvent.Cause.CANCEL, NOW);

        assertThat(result.reliabilityPenalty()).isEqualTo(-10);
        assertThat(match.getStatus()).isEqualTo(Match.Status.CANCELLED);
        verifyNoInteractions(requestRepository);
        verify(reputationService).adjustTeamReliability(opponentTeamId,
                new ReliabilityChange(-10, ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, matchId, true));
        verify(publisher).publishEvent(new MatchCancelledEvent(matchId, opponentTeamId, authorTeamId,
                Match.Status.CONFIRMED, MatchCancelledEvent.Cause.CANCEL, null, NOW));
    }

    @Test
    void acceptedRequestNotExactlyOneIsBrokenInvariant() {
        when(requestRepository.withdrawAccepted(matchId, opponentTeamId, "x", NOW)).thenReturn(List.of());

        assertThatThrownBy(() -> cancellation.cancelConfirmed(confirmedMatch(), opponentTeamId, "x",
                MatchCancelledEvent.Cause.CANCEL, NOW)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(reputationService, publisher);
    }
}
