package com.phananh.arenaflow.application.support.reputation;

import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
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
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Cấp / thu hồi huy hiệu xác minh đội ngay sau khi thao tác đánh giá / report / quyết định tranh chấp commit.
 * Huy hiệu là dữ liệu dẫn xuất: lỗi refresh chỉ ghi log, job đối soát hằng ngày sửa lại.
 */
@Slf4j
@Component
public class TeamVerificationRefresher {

    private final TeamRepository teamRepository;
    private final TransactionTemplate requiresNew;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public TeamVerificationRefresher(TeamRepository teamRepository, PlatformTransactionManager transactionManager,
                                     ApplicationEventPublisher eventPublisher, Clock clock) {
        this.teamRepository = teamRepository;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Gom id đội theo transaction đang chạy: lần gọi đầu bind một tập id (khoá = bean này) và đăng ký đúng một
     * synchronization; lần sau chỉ thêm id → mỗi transaction tối đa một câu refresh, chạy sau commit. Transaction
     * rollback → không refresh. {@code afterCompletion} luôn gỡ tập id (Spring không tự gỡ resource do code tự bind):
     * không gỡ thì lần thử lại của retrier hoặc request sau trên cùng thread thấy tập cũ và không đăng ký
     * synchronization mới → huy hiệu không được refresh mà không báo lỗi.
     */
    public void refreshAfterCommit(Collection<UUID> teamIds) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("TeamVerificationRefresher cần transaction đang chạy");
        }
        @SuppressWarnings("unchecked")
        Set<UUID> pending = (Set<UUID>) TransactionSynchronizationManager.getResource(this);
        if (pending == null) {
            Set<UUID> ids = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(this, ids);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    refreshNow(List.copyOf(ids));
                }

                @Override
                public void afterCompletion(int status) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(TeamVerificationRefresher.this);
                }
            });
            pending = ids;
        }
        teamIds.stream().filter(Objects::nonNull).forEach(pending::add);
    }

    /**
     * Refresh trong transaction mới (REQUIRES_NEW — bắt buộc khi gọi từ {@code afterCommit}), một câu cho mọi đội;
     * event phát trong transaction mới để người nghe sau commit nhận được. Lỗi → log WARN (chỉ id), không ném.
     */
    public void refreshNow(Collection<UUID> teamIds) {
        if (teamIds.isEmpty()) {
            return;
        }
        UUID[] ids = teamIds.toArray(UUID[]::new);
        try {
            requiresNew.executeWithoutResult(status -> {
                LocalDateTime now = LocalDateTime.now(clock);
                List<VerificationChangeView> changes = teamRepository.refreshVerification(ids, now,
                        ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE, ReputationRules.BADGE_MIN_AVG_EXCLUSIVE,
                        ReputationRules.BADGE_MIN_SCORE, ReputationRules.LATE_CANCEL_WINDOW_HOURS);
                changes.forEach(change -> eventPublisher.publishEvent(
                        new TeamVerificationChangedEvent(change.getTeamId(), Boolean.TRUE.equals(change.getVerified()), now)));
            });
        } catch (RuntimeException exception) {
            log.warn("Verification badge refresh failed for teams {}", List.of(ids), exception);
        }
    }
}
