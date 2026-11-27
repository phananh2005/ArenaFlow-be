package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.event.reputation.UserVerificationChangedEvent;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import com.phananh.arenaflow.domain.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Cấp / thu hồi huy hiệu xác minh (đội và cá nhân) sau khi thao tác gốc commit. Gom id theo transaction: mỗi request tối
 * đa 1 câu refresh đội + 1 câu refresh cá nhân, chạy trong transaction riêng. Lần thử bị rollback không refresh; lỗi
 * refresh chỉ log (job hằng ngày đối soát lại).
 */
@Slf4j
@Component
public class VerificationRefresher {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final TransactionTemplate requiresNew;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public VerificationRefresher(TeamRepository teamRepository, UserRepository userRepository,
                                 PlatformTransactionManager transactionManager, ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /** Ghi nhận đội cần refresh sau commit của transaction đang chạy (không có transaction → IllegalStateException). */
    public void refreshTeamsAfterCommit(Collection<UUID> teamIds) {
        pending().teams.addAll(teamIds);
    }

    /** Ghi nhận cá nhân cần refresh sau commit của transaction đang chạy. */
    public void refreshUsersAfterCommit(Collection<UUID> userIds) {
        pending().users.addAll(userIds);
    }

    /** Refresh ngay trong transaction mới; trả về sau khi phát event cho mỗi chủ thể vừa đổi trạng thái. */
    public void refreshNow(Collection<UUID> teamIds, Collection<UUID> userIds) {
        if (teamIds.isEmpty() && userIds.isEmpty()) {
            return;
        }
        try {
            requiresNew.executeWithoutResult(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                if (!teamIds.isEmpty()) {
                    List<VerificationChangeView> changed = teamRepository.refreshVerification(
                            teamIds.toArray(UUID[]::new), now, ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE,
                            ReputationRules.BADGE_MIN_AVG_EXCLUSIVE, ReputationRules.BADGE_MIN_SCORE);
                    changed.forEach(view -> eventPublisher.publishEvent(new TeamVerificationChangedEvent(
                            view.getSubjectId(), Boolean.TRUE.equals(view.getVerified()), now)));
                }
                if (!userIds.isEmpty()) {
                    List<VerificationChangeView> changed = userRepository.refreshVerification(
                            userIds.toArray(UUID[]::new), now, ReputationRules.CANCEL_WINDOW_HOURS,
                            ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE, ReputationRules.BADGE_MIN_AVG_EXCLUSIVE,
                            ReputationRules.BADGE_MIN_SCORE);
                    changed.forEach(view -> eventPublisher.publishEvent(new UserVerificationChangedEvent(
                            view.getSubjectId(), Boolean.TRUE.equals(view.getVerified()), now)));
                }
            });
        } catch (RuntimeException exception) {
            log.warn("Verification refresh failed: teams={}, users={}", teamIds, userIds, exception);
        }
    }

    /**
     * Tập id gom của transaction đang chạy; lần đầu đăng ký một synchronization. Resource luôn được gỡ ở
     * {@code afterCompletion} (cả commit lẫn rollback) để lần thử sau / request sau trên cùng thread bắt đầu sạch.
     */
    private Pending pending() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("VerificationRefresher requires an active transaction");
        }
        Pending existing = (Pending) TransactionSynchronizationManager.getResource(this);
        if (existing != null) {
            return existing;
        }
        Pending created = new Pending();
        TransactionSynchronizationManager.bindResource(this, created);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                refreshNow(Set.copyOf(created.teams), Set.copyOf(created.users));
            }

            @Override
            public void afterCompletion(int status) {
                TransactionSynchronizationManager.unbindResourceIfPossible(VerificationRefresher.this);
            }
        });
        return created;
    }

    private static final class Pending {
        private final Set<UUID> teams = new LinkedHashSet<>();
        private final Set<UUID> users = new LinkedHashSet<>();
    }
}
