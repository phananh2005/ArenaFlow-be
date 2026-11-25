package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.event.reputation.ReliabilityScoreChangedEvent;
import com.phananh.arenaflow.application.event.reputation.ReputationSubjectType;
import com.phananh.arenaflow.application.service.impl.ReputationServiceImpl;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReputationServiceImplTest {

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-21T03:00:00Z"), ZoneId.systemDefault());
    private final ReputationServiceImpl service = new ReputationServiceImpl(teamRepository, userRepository, publisher, clock);
    private final UUID subjectId = UUID.randomUUID();
    private final UUID referenceId = UUID.randomUUID();

    @Test
    void zeroDeltaWithoutStreakBreakRunsNoQuery() {
        service.adjustTeamReliability(subjectId, new ReliabilityChange(0, ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED,
                referenceId, false));

        verifyNoInteractions(teamRepository, publisher);
    }

    @Test
    void adjustsTeamInOneAtomicUpdateAndPublishesEvent() {
        when(teamRepository.adjustReliability(subjectId, -5, true)).thenReturn(1);

        service.adjustTeamReliability(subjectId, new ReliabilityChange(-5,
                ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, referenceId, true));

        verify(teamRepository).adjustReliability(subjectId, -5, true);
        ArgumentCaptor<ReliabilityScoreChangedEvent> event = ArgumentCaptor.forClass(ReliabilityScoreChangedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue()).isEqualTo(new ReliabilityScoreChangedEvent(ReputationSubjectType.TEAM, subjectId, -5,
                ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, referenceId, LocalDateTime.of(2026, 11, 21, 10, 0)));
    }

    @Test
    void missingTeamIsProgrammingError() {
        when(teamRepository.adjustReliability(subjectId, -2, true)).thenReturn(0);

        assertThatThrownBy(() -> service.adjustTeamReliability(subjectId, new ReliabilityChange(-2,
                ReliabilityReason.MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED, referenceId, true)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void adjustsUserWithSubjectTypeUser() {
        when(userRepository.adjustReliability(subjectId, -3, false)).thenReturn(1);

        service.adjustUserReliability(subjectId, new ReliabilityChange(-3,
                ReliabilityReason.MATCH_CANCELLED_AFTER_CONFIRMED, referenceId, false));

        ArgumentCaptor<ReliabilityScoreChangedEvent> event = ArgumentCaptor.forClass(ReliabilityScoreChangedEvent.class);
        verify(publisher).publishEvent(event.capture());
        assertThat(event.getValue().subjectType()).isEqualTo(ReputationSubjectType.USER);
    }
}
