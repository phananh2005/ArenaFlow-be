package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import com.phananh.arenaflow.domain.repository.PairingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MatchExpiryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchRequestRepository requestRepository = mock(MatchRequestRepository.class);
    private final PairingRepository pairingRepository = mock(PairingRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final MatchExpiry expiry = new MatchExpiry(matchRepository, requestRepository, pairingRepository, publisher);
    private final UUID matchId = UUID.randomUUID();
    private final UUID authorTeamId = UUID.randomUUID();

    @Test
    void expiresMatchAndPendingRequestsThenPublishesOneEvent() {
        when(matchRepository.expireIfDue(matchId, NOW)).thenReturn(1);

        boolean expired = expiry.expireIfDue(matchId, authorTeamId, Match.Type.HAS_VENUE, NOW);

        assertThat(expired).isTrue();
        verify(requestRepository).expirePendingByMatchIds(List.of(matchId));
        verify(publisher).publishEvent(new MatchExpiredEvent(matchId, authorTeamId, NOW));
        verifyNoInteractions(pairingRepository);
    }

    @Test
    void expiresNegotiatingPairingsOfNeedVenueMatchInsteadOfRequests() {
        when(matchRepository.expireIfDue(matchId, NOW)).thenReturn(1);

        assertThat(expiry.expireIfDue(matchId, authorTeamId, Match.Type.NEED_VENUE, NOW)).isTrue();

        verify(pairingRepository).expireNegotiatingByMatchIds(List.of(matchId), NOW);
        verifyNoInteractions(requestRepository);
        verify(publisher).publishEvent(new MatchExpiredEvent(matchId, authorTeamId, NOW));
    }

    @Test
    void doesNothingMoreWhenAnotherTransactionAlreadyExpiredIt() {
        when(matchRepository.expireIfDue(matchId, NOW)).thenReturn(0);

        assertThat(expiry.expireIfDue(matchId, authorTeamId, Match.Type.HAS_VENUE, NOW)).isFalse();
        verifyNoInteractions(requestRepository, pairingRepository, publisher);
    }
}
