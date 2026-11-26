package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.service.impl.TeamVerificationJobServiceImpl;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationProperties;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamVerificationJobServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T20:00:00Z"), ZoneId.systemDefault());
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 3, 0);

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final TeamVerificationJobService service = new TeamVerificationJobServiceImpl(teamRepository,
            new TransactionTemplate(transactionManager), publisher, new TeamVerificationProperties(2), CLOCK);
    private final UUID t1 = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private final UUID t2 = UUID.fromString("00000000-0000-7000-8000-000000000002");
    private final UUID t3 = UUID.fromString("00000000-0000-7000-8000-000000000003");

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    }

    @Test
    void walksBatchesByIdAndPublishesEventPerChangedTeam() {
        when(teamRepository.findIdsForVerification(isNull(), eq(2))).thenReturn(List.of(t1, t2));
        when(teamRepository.findIdsForVerification(eq(t2), eq(2))).thenReturn(List.of(t3));
        when(teamRepository.findIdsForVerification(eq(t3), eq(2))).thenReturn(List.of());
        when(teamRepository.refreshVerification(eq(new UUID[]{t1, t2}), eq(NOW), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(changed(t1, true)));
        when(teamRepository.refreshVerification(eq(new UUID[]{t3}), eq(NOW), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(changed(t3, false)));

        int changedCount = service.reconcileAll();

        assertThat(changedCount).isEqualTo(2);
        verify(publisher).publishEvent(new TeamVerificationChangedEvent(t1, true, NOW));
        verify(publisher).publishEvent(new TeamVerificationChangedEvent(t3, false, NOW));
    }

    @Test
    void failedBatchIsLoggedAndNextBatchStillRuns() {
        when(teamRepository.findIdsForVerification(isNull(), eq(2))).thenReturn(List.of(t1, t2));
        when(teamRepository.findIdsForVerification(eq(t2), eq(2))).thenReturn(List.of(t3));
        when(teamRepository.findIdsForVerification(eq(t3), eq(2))).thenReturn(List.of());
        when(teamRepository.refreshVerification(eq(new UUID[]{t1, t2}), any(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("batch failed"));
        when(teamRepository.refreshVerification(eq(new UUID[]{t3}), any(), anyInt(), anyDouble(), anyInt(), anyInt()))
                .thenReturn(List.of(changed(t3, true)));

        assertThat(service.reconcileAll()).isEqualTo(1);
        verify(publisher).publishEvent(new TeamVerificationChangedEvent(t3, true, NOW));
    }

    private VerificationChangeView changed(UUID teamId, boolean verified) {
        return new SpelAwareProxyProjectionFactory().createProjection(VerificationChangeView.class,
                Map.of("teamId", teamId, "verified", verified));
    }
}
