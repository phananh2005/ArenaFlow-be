package com.phananh.arenaflow.application.support.mercenary;

import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationWithdrawnEvent;
import com.phananh.arenaflow.application.support.ConcurrencyConflictException;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.projection.ApprovedCountView;
import com.phananh.arenaflow.domain.projection.WithdrawnApplicationView;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MercenaryAccountCleanupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 12, 0);

    private final MercenaryRecruitmentRepository recruitmentRepository = mock(MercenaryRecruitmentRepository.class);
    private final MercenaryApplicationRepository applicationRepository = mock(MercenaryApplicationRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final SpelAwareProxyProjectionFactory projections = new SpelAwareProxyProjectionFactory();
    private final MercenaryAccountCleanup cleanup = new MercenaryAccountCleanup(recruitmentRepository,
            applicationRepository, eventPublisher);
    private final UUID userId = UUID.randomUUID();
    private final UUID recruitmentId = UUID.randomUUID();

    @Test
    void locksRecruitmentsBeforeApplicationsAndPublishesPerApplication() {
        when(recruitmentRepository.decrementApprovedOfApplicant(userId, NOW)).thenReturn(List.of(count(1L)));
        when(applicationRepository.withdrawActiveOfApplicant(userId, NOW)).thenReturn(List.of(
                withdrawn(true), withdrawn(false)));

        cleanup.withdrawForDeletedApplicant(userId, NOW);

        InOrder order = inOrder(recruitmentRepository, applicationRepository);
        order.verify(recruitmentRepository).decrementApprovedOfApplicant(userId, NOW);
        order.verify(applicationRepository).withdrawActiveOfApplicant(userId, NOW);
        ArgumentCaptor<MercenaryApplicationWithdrawnEvent> events =
                ArgumentCaptor.forClass(MercenaryApplicationWithdrawnEvent.class);
        verify(eventPublisher, times(2)).publishEvent(events.capture());
        assertThat(events.getAllValues()).extracting(MercenaryApplicationWithdrawnEvent::previousStatus)
                .containsExactly(MercenaryApplication.Status.APPROVED, MercenaryApplication.Status.PENDING);
        assertThat(events.getAllValues()).allSatisfy(event ->
                assertThat(event.cause()).isEqualTo(MercenaryApplicationWithdrawnEvent.Cause.ACCOUNT_DELETED));
    }

    @Test
    void mismatchBetweenDecrementAndWithdrawnApprovalsSignalsRetry() {
        when(recruitmentRepository.decrementApprovedOfApplicant(userId, NOW)).thenReturn(List.of(count(1L)));
        when(applicationRepository.withdrawActiveOfApplicant(userId, NOW)).thenReturn(List.of(
                withdrawn(true), withdrawn(true)));

        assertThatThrownBy(() -> cleanup.withdrawForDeletedApplicant(userId, NOW))
                .isInstanceOf(ConcurrencyConflictException.class);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private ApprovedCountView count(long n) {
        return projections.createProjection(ApprovedCountView.class, Map.of("recruitmentId", recruitmentId, "n", n));
    }

    private WithdrawnApplicationView withdrawn(boolean wasApproved) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", UUID.randomUUID());
        values.put("recruitmentId", recruitmentId);
        values.put("teamId", UUID.randomUUID());
        values.put("matchId", UUID.randomUUID());
        values.put("wasApproved", wasApproved);
        return projections.createProjection(WithdrawnApplicationView.class, values);
    }
}
