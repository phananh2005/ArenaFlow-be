package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.reputation.ReliabilityScoreChangedEvent;
import com.phananh.arenaflow.application.event.reputation.ReputationSubjectType;
import com.phananh.arenaflow.application.service.ReliabilityChange;
import com.phananh.arenaflow.application.service.ReputationService;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReputationServiceImpl implements ReputationService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustTeamReliability(UUID teamId, ReliabilityChange change) {
        if (isNoOp(change)) {
            return;
        }
        int updated = teamRepository.adjustReliability(teamId, change.delta(), change.breaksCleanStreak());
        publish(ReputationSubjectType.TEAM, teamId, change, updated);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void adjustUserReliability(UUID userId, ReliabilityChange change) {
        if (isNoOp(change)) {
            return;
        }
        int updated = userRepository.adjustReliability(userId, change.delta(), change.breaksCleanStreak());
        publish(ReputationSubjectType.USER, userId, change, updated);
    }

    private static boolean isNoOp(ReliabilityChange change) {
        return change.delta() == 0 && !change.breaksCleanStreak();
    }

    /** 0 dòng = chủ thể không tồn tại: caller đã kiểm trước nên đây là bất biến bị phá. */
    private void publish(ReputationSubjectType type, UUID subjectId, ReliabilityChange change, int updated) {
        if (updated != 1) {
            throw new IllegalStateException("Reliability subject not found: " + type + " " + subjectId);
        }
        eventPublisher.publishEvent(new ReliabilityScoreChangedEvent(type, subjectId, change.delta(), change.reason(),
                change.referenceId(), LocalDateTime.now(clock)));
    }
}
