package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.service.RetentionJobService;
import com.phananh.arenaflow.application.support.notification.NotificationProperties;
import com.phananh.arenaflow.application.support.notification.NotificationRules;
import com.phananh.arenaflow.domain.repository.RetentionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.function.IntSupplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionJobServiceImpl implements RetentionJobService {

    private final RetentionRepository retentionRepository;
    private final TransactionTemplate transactionTemplate;
    private final NotificationProperties properties;
    private final Clock clock;

    @Override
    public int purgeExpiredConversations() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(NotificationRules.CHAT_RETENTION);
        int batchSize = properties.retention().conversationBatchSize();
        return deleteInBatches("conversations", batchSize,
                () -> retentionRepository.deleteExpiredConversations(cutoff, batchSize));
    }

    @Override
    public int purgeExpiredNotifications() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(NotificationRules.RETENTION);
        int batchSize = properties.retention().notificationBatchSize();
        return deleteInBatches("notifications", batchSize,
                () -> retentionRepository.deleteExpiredNotifications(cutoff, batchSize));
    }

    @Override
    public int purgeOldReminders() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(NotificationRules.RETENTION);
        Integer deleted = transactionTemplate.execute(status -> retentionRepository.deleteRemindersSentBefore(cutoff));
        int count = deleted == null ? 0 : deleted;
        log.info("Retention purge finished: reminders={}", count);
        return count;
    }

    /** Mỗi lô một transaction ngắn; dừng khi lô thiếu, hết số lô tối đa, hoặc lô lỗi (lần chạy sau làm tiếp). */
    private int deleteInBatches(String target, int batchSize, IntSupplier deleteBatch) {
        int total = 0;
        for (int index = 0; index < properties.retention().maxBatchesPerRun(); index++) {
            Integer deleted;
            try {
                deleted = transactionTemplate.execute(status -> deleteBatch.getAsInt());
            } catch (RuntimeException exception) {
                log.error("Retention purge of {} failed at batch {}; stopping this task", target, index + 1, exception);
                break;
            }
            int count = deleted == null ? 0 : deleted;
            total += count;
            if (count < batchSize) {
                break;
            }
        }
        log.info("Retention purge finished: {}={}", target, total);
        return total;
    }
}
