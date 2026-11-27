package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationRejectedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.service.MercenaryLifecycleJobService;
import com.phananh.arenaflow.application.support.mercenary.MercenaryLifecycleProperties;
import com.phananh.arenaflow.domain.projection.DueRecruitmentView;
import com.phananh.arenaflow.domain.projection.GameCancelNoticeView;
import com.phananh.arenaflow.domain.projection.OrphanRejectedApplicationView;
import com.phananh.arenaflow.domain.projection.RejectedApplicationView;
import com.phananh.arenaflow.domain.repository.MercenaryApplicationRepository;
import com.phananh.arenaflow.domain.repository.MercenaryRecruitmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MercenaryLifecycleJobServiceImpl implements MercenaryLifecycleJobService {

    private final MercenaryRecruitmentRepository recruitmentRepository;
    private final MercenaryApplicationRepository applicationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final MercenaryLifecycleProperties properties;
    private final Clock clock;

    /**
     * Mỗi lô một transaction (3 truy vấn): nhận tin tới hạn (bỏ qua dòng instance khác đang giữ), ghi CLOSED / EXPIRED,
     * từ chối đơn chờ, phát event mỗi tin kèm id đơn bị từ chối.
     */
    @Override
    public int closeDueRecruitments() {
        LocalDateTime now = LocalDateTime.now(clock);
        return runBatches("close due recruitments", () -> {
            List<DueRecruitmentView> due = recruitmentRepository.claimDue(now, properties.batchSize());
            if (due.isEmpty()) {
                return 0;
            }
            Map<Boolean, List<UUID>> byCancelled = due.stream().collect(Collectors.partitioningBy(
                    view -> "MATCH_CANCELLED".equals(view.getCause()),
                    Collectors.mapping(DueRecruitmentView::getId, Collectors.toList())));
            recruitmentRepository.markClosedOrExpired(byCancelled.get(true).toArray(UUID[]::new),
                    byCancelled.get(false).toArray(UUID[]::new), now);
            Map<UUID, List<UUID>> rejected = applicationRepository.rejectPendingByRecruitmentIds(
                            due.stream().map(DueRecruitmentView::getId).toArray(UUID[]::new), now).stream()
                    .collect(Collectors.groupingBy(RejectedApplicationView::getRecruitmentId,
                            Collectors.mapping(RejectedApplicationView::getId, Collectors.toList())));
            for (DueRecruitmentView view : due) {
                eventPublisher.publishEvent(new MercenaryRecruitmentClosedEvent(view.getId(), view.getMatchId(),
                        view.getExternalMatchId(), view.getTeamId(), cause(view),
                        List.copyOf(rejected.getOrDefault(view.getId(), List.of())), now));
            }
            return due.size();
        });
    }

    /** Mỗi lô một transaction (1 truy vấn): đơn chờ còn sót trên tin đã đóng → REJECTED, báo ứng viên. */
    @Override
    public int rejectOrphanPending() {
        LocalDateTime now = LocalDateTime.now(clock);
        return runBatches("reject orphan pending applications", () -> {
            List<OrphanRejectedApplicationView> rows = applicationRepository.rejectOrphanPending(now,
                    properties.batchSize());
            rows.forEach(row -> eventPublisher.publishEvent(new MercenaryApplicationRejectedEvent(row.getId(),
                    row.getRecruitmentId(), row.getMatchId(), row.getExternalMatchId(), row.getTeamId(),
                    row.getApplicantId(), MercenaryApplicationRejectedEvent.Cause.RECRUITMENT_CLOSED, now)));
            return rows.size();
        });
    }

    /**
     * Mỗi lô một transaction (1 truy vấn): đánh dấu đã báo cho đơn APPROVED của trận bị huỷ trong khoảng nhìn lại, gom
     * theo trận và phát một event mỗi trận.
     */
    @Override
    public int notifyCancelledGames() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lookbackStart = now.minusDays(properties.cancelNoticeLookbackDays());
        return runBatches("notify cancelled games", () -> {
            List<GameCancelNoticeView> rows = applicationRepository.markGameCancelNotified(now, lookbackStart,
                    properties.batchSize());
            Map<UUID, List<GameCancelNoticeView>> byGame = rows.stream().collect(Collectors.groupingBy(
                    row -> row.getMatchId() != null ? row.getMatchId() : row.getExternalMatchId(), LinkedHashMap::new,
                    Collectors.toList()));
            byGame.values().forEach(group -> {
                GameCancelNoticeView first = group.get(0);
                eventPublisher.publishEvent(new MercenaryGameCancelledEvent(first.getMatchId(),
                        first.getExternalMatchId(), group.stream().map(GameCancelNoticeView::getId).toList(), now));
            });
            return rows.size();
        });
    }

    /**
     * Lặp lô tới khi lô thiếu hoặc hết số lô mỗi lượt; mỗi lô một transaction. Lô lỗi → log ERROR, lô sau vẫn chạy.
     */
    private int runBatches(String task, Supplier<Integer> batch) {
        int total = 0;
        for (int index = 0; index < properties.maxBatchesPerRun(); index++) {
            Integer processed;
            try {
                processed = transactionTemplate.execute(status -> batch.get());
            } catch (RuntimeException exception) {
                log.error("Mercenary job '{}' batch {} failed", task, index + 1, exception);
                continue;
            }
            int count = processed == null ? 0 : processed;
            total += count;
            if (count < properties.batchSize()) {
                break;
            }
        }
        log.info("Mercenary job '{}' finished: {} rows", task, total);
        return total;
    }

    private static MercenaryRecruitmentClosedEvent.Cause cause(DueRecruitmentView view) {
        if (!"MATCH_CANCELLED".equals(view.getCause())) {
            return MercenaryRecruitmentClosedEvent.Cause.EXPIRED;
        }
        return view.getExternalMatchId() != null ? MercenaryRecruitmentClosedEvent.Cause.EXTERNAL_MATCH_CANCELLED
                : MercenaryRecruitmentClosedEvent.Cause.MATCH_CANCELLED;
    }
}
