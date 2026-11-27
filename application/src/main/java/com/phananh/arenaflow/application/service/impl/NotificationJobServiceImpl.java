package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.service.NotificationJobService;
import com.phananh.arenaflow.application.support.notification.NotificationDraft;
import com.phananh.arenaflow.application.support.notification.NotificationProperties;
import com.phananh.arenaflow.application.support.notification.NotificationRules;
import com.phananh.arenaflow.application.support.notification.NotificationTexts;
import com.phananh.arenaflow.application.support.notification.NotificationTexts.Text;
import com.phananh.arenaflow.application.support.notification.NotificationWriter;
import com.phananh.arenaflow.domain.entity.chat.Notification.ReferenceType;
import com.phananh.arenaflow.domain.entity.chat.Notification.Type;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.GameAudienceRowView;
import com.phananh.arenaflow.domain.projection.ReminderGameView;
import com.phananh.arenaflow.domain.repository.MatchReminderRepository;
import com.phananh.arenaflow.domain.repository.NotificationAudienceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationJobServiceImpl implements NotificationJobService {

    private static final String[] CONFIRMED = {Match.Status.CONFIRMED.name()};
    private static final String[] NO_STATUSES = new String[0];
    private static final UUID[] NO_IDS = new UUID[0];

    private final MatchReminderRepository matchReminderRepository;
    private final NotificationAudienceRepository audienceRepository;
    private final NotificationWriter notificationWriter;
    private final NotificationTexts texts;
    private final TransactionTemplate transactionTemplate;
    private final NotificationProperties properties;
    private final Clock clock;

    private record BatchResult(int games, int notifications) {
    }

    /**
     * Claim không khoá dòng kèo: dấu nhắc unique theo trận bảo đảm mỗi trận nhắc đúng một lần. Kèo bị huỷ giữa
     * claim và đọc người nhận thì câu đọc lọc theo trạng thái nên không nhắc (dấu vẫn giữ — không nhắc lại).
     */
    @Override
    public int sendDueStartReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime leadEnd = now.plus(NotificationRules.REMINDER_LEAD);
        long leadSeconds = NotificationRules.REMINDER_LEAD.toSeconds();
        return runBatches("start reminder", now, () -> {
            List<ReminderGameView> claimed = matchReminderRepository.claimDueStart(now, leadEnd, leadSeconds,
                    properties.reminder().batchSize());
            if (claimed.isEmpty()) {
                return new BatchResult(0, 0);
            }
            UUID[] matchIds = claimed.stream().map(ReminderGameView::getMatchId).filter(Objects::nonNull)
                    .toArray(UUID[]::new);
            UUID[] externalIds = claimed.stream().map(ReminderGameView::getExternalMatchId).filter(Objects::nonNull)
                    .toArray(UUID[]::new);
            List<NotificationDraft> drafts = audienceRepository.findGameAudience(matchIds, CONFIRMED, externalIds).stream()
                    .map(this::startReminder).toList();
            return new BatchResult(claimed.size(), write(drafts, now));
        });
    }

    /** Kèo trên hệ thống nhắc qua sự kiện kèo kết thúc; ở đây chỉ trận ngoài (không có trạng thái "đã kết thúc"). */
    @Override
    public int sendDueRatingReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime lookbackStart = now.minus(properties.reminder().ratingLookback());
        return runBatches("rating reminder", now, () -> {
            List<UUID> claimed = matchReminderRepository.claimDueRating(now, lookbackStart,
                    properties.reminder().batchSize());
            if (claimed.isEmpty()) {
                return new BatchResult(0, 0);
            }
            List<NotificationDraft> drafts = audienceRepository.findGameAudience(NO_IDS, NO_STATUSES,
                    claimed.toArray(UUID[]::new)).stream()
                    .filter(row -> "MERCENARY".equals(row.getKind()))
                    .map(row -> draft(row, Type.MERCENARY_RATING_REMINDER,
                            texts.mercenaryRatingReminder(row.getTeamName(), row.getStartTime(), row.getPosition())))
                    .toList();
            return new BatchResult(claimed.size(), write(drafts, now));
        });
    }

    /** Mỗi lô một transaction; dừng khi lô thiếu, hết số lô tối đa, hoặc lô lỗi (lần chạy sau làm tiếp). */
    private int runBatches(String task, LocalDateTime now, Supplier<BatchResult> batch) {
        int games = 0;
        int notifications = 0;
        for (int index = 0; index < properties.reminder().maxBatchesPerRun(); index++) {
            BatchResult result;
            try {
                result = transactionTemplate.execute(status -> batch.get());
            } catch (RuntimeException exception) {
                log.error("Notification {} batch {} failed; stopping this run", task, index + 1, exception);
                break;
            }
            games += result.games();
            notifications += result.notifications();
            if (result.games() < properties.reminder().batchSize()) {
                break;
            }
        }
        log.info("Notification {} run at {} finished: {} games, {} notifications", task, now, games, notifications);
        return games;
    }

    private NotificationDraft startReminder(GameAudienceRowView row) {
        if ("CAPTAIN".equals(row.getKind())) {
            Text text = texts.reminderForCaptain(row.getOpponentTeamName(), row.getStartTime(), row.getVenueName());
            return new NotificationDraft(row.getRecipientUserId(), Type.REMINDER_BEFORE_MATCH, text.title(), text.body(),
                    ReferenceType.MATCH, row.getMatchId());
        }
        return draft(row, Type.REMINDER_BEFORE_MATCH, texts.reminderForMercenary(row.getTeamName(), row.getPosition(),
                row.getStartTime(), row.getVenueName()));
    }

    private static NotificationDraft draft(GameAudienceRowView row, Type type, Text text) {
        return new NotificationDraft(row.getRecipientUserId(), type, text.title(), text.body(),
                ReferenceType.MERCENARY_APPLICATION, row.getApplicationId());
    }

    private int write(List<NotificationDraft> drafts, LocalDateTime now) {
        return drafts.isEmpty() ? 0 : notificationWriter.write(drafts, now);
    }
}
