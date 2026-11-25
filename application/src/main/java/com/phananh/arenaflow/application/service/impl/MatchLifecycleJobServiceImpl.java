package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.event.match.MatchArchivedEvent;
import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.application.service.MatchLifecycleJobService;
import com.phananh.arenaflow.application.support.match.MatchLifecycleProperties;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.DueMatchView;
import com.phananh.arenaflow.domain.repository.MatchRepository;
import com.phananh.arenaflow.domain.repository.MatchRequestRepository;
import com.phananh.arenaflow.domain.repository.PairingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Mỗi lô một transaction: claim (khoá SKIP LOCKED) → UPDATE trạng thái → event mỗi kèo (listener chạy sau commit nên
 * lô rollback không gửi thông báo). Dừng khi lô ngắn hơn kích thước lô, đủ số lô tối đa, hoặc lô lỗi (log, lần sau
 * làm tiếp — điều kiện trạng thái là cổng chống trùng). {@code now} cố định cho cả lượt chạy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchLifecycleJobServiceImpl implements MatchLifecycleJobService {

    private final MatchRepository matchRepository;
    private final MatchRequestRepository matchRequestRepository;
    private final PairingRepository pairingRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final MatchLifecycleProperties properties;
    private final Clock clock;

    @Override
    public int expireDueMatches() {
        LocalDateTime now = LocalDateTime.now(clock);
        int expired = runBatches("expire", now, matchRepository::claimDueForExpiry, (claimed, ids) -> {
            matchRepository.markExpired(ids, now);
            List<UUID> flowA = idsOfType(claimed, Match.Type.HAS_VENUE);
            List<UUID> flowB = idsOfType(claimed, Match.Type.NEED_VENUE);
            if (!flowA.isEmpty()) {
                matchRequestRepository.expirePendingByMatchIds(flowA);
            }
            if (!flowB.isEmpty()) {
                pairingRepository.expireNegotiatingByMatchIds(flowB, now);
            }
            claimed.forEach(match -> eventPublisher.publishEvent(
                    new MatchExpiredEvent(match.getId(), match.getAuthorTeamId(), now)));
            return null;
        });
        log.info("Match expiry run finished: {} matches expired", expired);
        return expired;
    }

    @Override
    public int archiveFinishedMatches() {
        LocalDateTime now = LocalDateTime.now(clock);
        int archived = runBatches("archive", now, matchRepository::claimDueForArchive, (claimed, ids) -> {
            matchRepository.markArchived(ids, now);
            claimed.forEach(match -> eventPublisher.publishEvent(new MatchArchivedEvent(match.getId(),
                    match.getAuthorTeamId(), match.getOpponentTeamId(), match.getMatchEndTime(), now)));
            return null;
        });
        log.info("Match archive run finished: {} matches archived", archived);
        return archived;
    }

    /** Id các kèo trong lô thuộc một loại (luồng A: yêu cầu ghép; luồng B: thương lượng). */
    private static List<UUID> idsOfType(List<DueMatchView> claimed, Match.Type type) {
        return claimed.stream().filter(match -> type.name().equals(match.getType())).map(DueMatchView::getId).toList();
    }

    private int runBatches(String task, LocalDateTime now, BiFunction<LocalDateTime, Integer, List<DueMatchView>> claim,
                           BiFunction<List<DueMatchView>, List<UUID>, Void> apply) {
        int total = 0;
        for (int batch = 0; batch < properties.maxBatchesPerRun(); batch++) {
            List<DueMatchView> claimed;
            try {
                claimed = transactionTemplate.execute(status -> {
                    List<DueMatchView> rows = claim.apply(now, properties.batchSize());
                    if (!rows.isEmpty()) {
                        apply.apply(rows, rows.stream().map(DueMatchView::getId).toList());
                    }
                    return rows;
                });
            } catch (RuntimeException exception) {
                log.error("Match {} batch {} failed; stopping this task", task, batch + 1, exception);
                break;
            }
            total += claimed.size();
            if (claimed.size() < properties.batchSize()) {
                break;
            }
        }
        return total;
    }
}
