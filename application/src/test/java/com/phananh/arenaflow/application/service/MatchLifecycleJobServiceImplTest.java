package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.match.MatchArchivedEvent;
import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.application.service.impl.MatchLifecycleJobServiceImpl;
import com.phananh.arenaflow.application.support.match.MatchLifecycleProperties;
import com.phananh.arenaflow.domain.projection.DueMatchView;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchLifecycleJobServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 21, 10, 0);

    private final MatchRepository matchRepository = mock(MatchRepository.class);
    private final MatchRequestRepository requestRepository = mock(MatchRequestRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault());
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private MatchLifecycleJobServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new MatchLifecycleJobServiceImpl(matchRepository, requestRepository, publisher, transactionTemplate,
                new MatchLifecycleProperties(2, 3), clock);
        when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> ((TransactionCallback<Object>) invocation.getArgument(0)).doInTransaction(null));
    }

    private DueMatchView due(UUID id, UUID author, UUID opponent) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("authorTeamId", author);
        row.put("opponentTeamId", opponent);
        row.put("matchEndTime", NOW.minusHours(1));
        return projections.createProjection(DueMatchView.class, row);
    }

    @Test
    void expiresBatchesUntilShortBatchAndPublishesOneEventPerMatch() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        when(matchRepository.claimDueForExpiry(NOW, 2))
                .thenReturn(List.of(due(a, author, null), due(b, author, null)), List.of(due(c, author, null)));

        assertThat(service.expireDueMatches()).isEqualTo(3);

        verify(matchRepository).markExpired(List.of(a, b), NOW);
        verify(requestRepository).expirePendingByMatchIds(List.of(a, b));
        verify(matchRepository).markExpired(List.of(c), NOW);
        verify(publisher).publishEvent(new MatchExpiredEvent(a, author, NOW));
        verify(publisher).publishEvent(new MatchExpiredEvent(c, author, NOW));
        verify(matchRepository, times(2)).claimDueForExpiry(NOW, 2);
    }

    @Test
    void emptyClaimRunsNoUpdate() {
        when(matchRepository.claimDueForExpiry(NOW, 2)).thenReturn(List.of());

        assertThat(service.expireDueMatches()).isZero();
        verify(matchRepository, never()).markExpired(any(), any());
        verify(requestRepository, never()).expirePendingByMatchIds(any());
    }

    @Test
    void stopsAtMaxBatchesPerRun() {
        when(matchRepository.claimDueForExpiry(NOW, 2)).thenAnswer(invocation ->
                List.of(due(UUID.randomUUID(), UUID.randomUUID(), null), due(UUID.randomUUID(), UUID.randomUUID(), null)));

        assertThat(service.expireDueMatches()).isEqualTo(6);
        verify(matchRepository, times(3)).claimDueForExpiry(NOW, 2);
    }

    @Test
    void failedBatchStopsThisTaskWithoutEvents() {
        when(matchRepository.claimDueForExpiry(NOW, 2)).thenThrow(new IllegalStateException("db down"));

        assertThat(service.expireDueMatches()).isZero();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void archivesConfirmedMatchesWithoutTouchingRequests() {
        UUID id = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        UUID opponent = UUID.randomUUID();
        when(matchRepository.claimDueForArchive(NOW, 2)).thenReturn(List.of(due(id, author, opponent)));

        assertThat(service.archiveFinishedMatches()).isEqualTo(1);

        verify(matchRepository).markArchived(List.of(id), NOW);
        verify(publisher).publishEvent(new MatchArchivedEvent(id, author, opponent, NOW.minusHours(1), NOW));
        verify(requestRepository, never()).expirePendingByMatchIds(any());
    }
}
