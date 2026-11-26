package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.service.TeamVerificationJobService;
import com.phananh.arenaflow.application.support.reputation.ReputationRules;
import com.phananh.arenaflow.application.support.reputation.TeamVerificationProperties;
import com.phananh.arenaflow.domain.projection.VerificationChangeView;
import com.phananh.arenaflow.domain.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class TeamVerificationJobServiceImpl implements TeamVerificationJobService {

    private final TeamRepository teamRepository;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final TeamVerificationProperties properties;
    private final Clock clock;

    public TeamVerificationJobServiceImpl(TeamRepository teamRepository, TransactionTemplate transactionTemplate,
                                          ApplicationEventPublisher eventPublisher, TeamVerificationProperties properties,
                                          Clock clock) {
        this.teamRepository = teamRepository;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Không claim theo hàng đợi: câu refresh chỉ ghi đội có trạng thái huy hiệu đổi → hai instance chạy trùng không
     * phát event trùng. Lô lỗi rollback, log ERROR rồi đi tiếp từ id cuối của lô đó; không đọc được lô id → dừng lượt.
     */
    @Override
    public int reconcileAll() {
        LocalDateTime now = LocalDateTime.now(clock);
        UUID afterId = null;
        int changed = 0;
        int granted = 0;
        while (true) {
            AtomicReference<List<UUID>> batch = new AtomicReference<>(List.of());
            UUID from = afterId;
            try {
                List<VerificationChangeView> changes = transactionTemplate.execute(status -> {
                    List<UUID> ids = teamRepository.findIdsForVerification(from, properties.batchSize());
                    batch.set(ids);
                    if (ids.isEmpty()) {
                        return List.<VerificationChangeView>of();
                    }
                    List<VerificationChangeView> rows = teamRepository.refreshVerification(ids.toArray(UUID[]::new), now,
                            ReputationRules.BADGE_MIN_COMPLETED_EXCLUSIVE, ReputationRules.BADGE_MIN_AVG_EXCLUSIVE,
                            ReputationRules.BADGE_MIN_SCORE, ReputationRules.LATE_CANCEL_WINDOW_HOURS);
                    rows.forEach(row -> eventPublisher.publishEvent(new TeamVerificationChangedEvent(row.getTeamId(),
                            Boolean.TRUE.equals(row.getVerified()), now)));
                    return rows;
                });
                changed += changes.size();
                granted += (int) changes.stream().filter(row -> Boolean.TRUE.equals(row.getVerified())).count();
            } catch (RuntimeException exception) {
                log.error("Verification badge reconcile batch after {} failed", from, exception);
            }
            List<UUID> ids = batch.get();
            if (ids.isEmpty()) {
                break;
            }
            afterId = ids.get(ids.size() - 1);
        }
        log.info("Verification badge reconcile finished: {} granted, {} revoked", granted, changed - granted);
        return changed;
    }
}
