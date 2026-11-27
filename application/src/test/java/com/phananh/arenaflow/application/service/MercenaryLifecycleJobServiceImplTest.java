package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationRejectedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.service.impl.MercenaryLifecycleJobServiceImpl;
import com.phananh.arenaflow.application.support.mercenary.MercenaryLifecycleProperties;
import com.phananh.arenaflow.domain.projection.DueRecruitmentView;
import com.phananh.arenaflow.domain.projection.GameCancelNoticeView;
import com.phananh.arenaflow.domain.projection.OrphanRejectedApplicationView;
import com.phananh.arenaflow.domain.projection.RejectedApplicationView;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MercenaryLifecycleJobServiceImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final MercenaryRecruitmentRepository recruitmentRepository = mock(MercenaryRecruitmentRepository.class);
    private final MercenaryApplicationRepository applicationRepository = mock(MercenaryApplicationRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private MercenaryLifecycleJobServiceImpl service;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new MercenaryLifecycleJobServiceImpl(recruitmentRepository, applicationRepository, eventPublisher,
                new TransactionTemplate(transactionManager), new MercenaryLifecycleProperties(2, 3, 7),
                Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()));
    }

    @Test
    void closeDueSplitsCausesRejectsPendingAndStopsOnShortBatch() {
        UUID cancelled = UUID.randomUUID();
        UUID expired = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        when(recruitmentRepository.claimDue(NOW, 2)).thenReturn(
                List.of(due(cancelled, "MATCH_CANCELLED"), due(expired, "EXPIRED")),
                List.of(due(UUID.randomUUID(), "EXPIRED")));
        when(applicationRepository.rejectPendingByRecruitmentIds(any(), eq(NOW))).thenReturn(List.of(
                projections.createProjection(RejectedApplicationView.class,
                        Map.of("id", rejected, "recruitmentId", cancelled))), List.of());

        int closed = service.closeDueRecruitments();

        assertThat(closed).isEqualTo(3);
        ArgumentCaptor<UUID[]> closedIds = ArgumentCaptor.forClass(UUID[].class);
        ArgumentCaptor<UUID[]> expiredIds = ArgumentCaptor.forClass(UUID[].class);
        verify(recruitmentRepository, times(2)).markClosedOrExpired(closedIds.capture(), expiredIds.capture(), eq(NOW));
        assertThat(closedIds.getAllValues().get(0)).containsExactly(cancelled);
        assertThat(expiredIds.getAllValues().get(0)).containsExactly(expired);
        ArgumentCaptor<MercenaryRecruitmentClosedEvent> events =
                ArgumentCaptor.forClass(MercenaryRecruitmentClosedEvent.class);
        verify(eventPublisher, times(3)).publishEvent(events.capture());
        MercenaryRecruitmentClosedEvent first = events.getAllValues().get(0);
        assertThat(first.cause()).isEqualTo(MercenaryRecruitmentClosedEvent.Cause.MATCH_CANCELLED);
        assertThat(first.rejectedApplicationIds()).containsExactly(rejected);
        assertThat(events.getAllValues().get(1).cause()).isEqualTo(MercenaryRecruitmentClosedEvent.Cause.EXPIRED);
    }

    @Test
    void failedBatchIsLoggedAndNextBatchStillRuns() {
        when(recruitmentRepository.claimDue(NOW, 2)).thenThrow(new IllegalStateException("db"))
                .thenReturn(List.of());

        assertThat(service.closeDueRecruitments()).isZero();
        verify(recruitmentRepository, times(2)).claimDue(NOW, 2);
    }

    @Test
    void batchesAreCappedPerRun() {
        when(recruitmentRepository.claimDue(NOW, 2)).thenAnswer(invocation ->
                List.of(due(UUID.randomUUID(), "EXPIRED"), due(UUID.randomUUID(), "EXPIRED")));
        when(applicationRepository.rejectPendingByRecruitmentIds(any(), any())).thenReturn(List.of());

        assertThat(service.closeDueRecruitments()).isEqualTo(6);
        verify(recruitmentRepository, times(3)).claimDue(NOW, 2);
    }

    @Test
    void orphanPendingApplicationsAreRejectedWithRecruitmentClosedCause() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", UUID.randomUUID());
        row.put("recruitmentId", UUID.randomUUID());
        row.put("applicantId", UUID.randomUUID());
        row.put("teamId", UUID.randomUUID());
        when(applicationRepository.rejectOrphanPending(NOW, 2)).thenReturn(List.of(
                projections.createProjection(OrphanRejectedApplicationView.class, row)));

        assertThat(service.rejectOrphanPending()).isEqualTo(1);
        ArgumentCaptor<MercenaryApplicationRejectedEvent> event =
                ArgumentCaptor.forClass(MercenaryApplicationRejectedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().cause()).isEqualTo(MercenaryApplicationRejectedEvent.Cause.RECRUITMENT_CLOSED);
    }

    @Test
    void cancelledGameNoticesAreGroupedPerGame() {
        UUID matchId = UUID.randomUUID();
        UUID externalId = UUID.randomUUID();
        when(applicationRepository.markGameCancelNotified(NOW, NOW.minusDays(7), 2)).thenReturn(
                List.of(notice(matchId, null), notice(matchId, null)), List.of(notice(null, externalId)));

        assertThat(service.notifyCancelledGames()).isEqualTo(3);
        ArgumentCaptor<MercenaryGameCancelledEvent> events = ArgumentCaptor.forClass(MercenaryGameCancelledEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues().get(0).matchId()).isEqualTo(matchId);
        assertThat(events.getAllValues().get(0).applicationIds()).hasSize(2);
        assertThat(events.getAllValues().get(1).externalMatchId()).isEqualTo(externalId);
        verify(applicationRepository, times(2)).markGameCancelNotified(any(), any(), anyInt());
    }

    private DueRecruitmentView due(UUID id, String cause) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", id);
        values.put("matchId", UUID.randomUUID());
        values.put("teamId", UUID.randomUUID());
        values.put("cause", cause);
        return projections.createProjection(DueRecruitmentView.class, values);
    }

    private GameCancelNoticeView notice(UUID matchId, UUID externalMatchId) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("matchId", matchId);
        values.put("externalMatchId", externalMatchId);
        return projections.createProjection(GameCancelNoticeView.class, values);
    }
}
