package com.phananh.arenaflow.application.support.notification;

import com.phananh.arenaflow.application.mapper.NotificationMapper;
import com.phananh.arenaflow.application.support.realtime.UserFrame;
import com.phananh.arenaflow.application.support.realtime.UserFrameDelivery;
import com.phananh.arenaflow.application.support.realtime.UserRealtimeGateway;
import com.phananh.arenaflow.domain.entity.chat.Notification;
import com.phananh.arenaflow.domain.projection.InsertedNotificationView;
import com.phananh.arenaflow.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Ghi thông báo gom lô trong transaction của người gọi, rồi đẩy frame realtime và push sau commit. */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationWriter {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final UserRealtimeGateway userRealtimeGateway;
    private final PushSender pushSender;

    private record DedupKey(UUID recipient, Notification.Type type, Notification.ReferenceType referenceType,
                            UUID referenceId) {
    }

    /**
     * Phải chạy trong transaction đang có. Bỏ bản nháp không có người nhận, khử trùng theo (người nhận, loại, đối
     * tượng), ghi cả lô bằng đúng một câu (bỏ câu khi không còn gì); người nhận đã xoá tài khoản bị loại trong câu
     * ghi. Sau commit: frame NOTIFICATION_CREATED cho từng dòng đã ghi và gọi {@link PushSender}. Trả số dòng đã ghi.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int write(List<NotificationDraft> drafts, LocalDateTime now) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("NotificationWriter requires an active transaction");
        }
        Map<DedupKey, NotificationDraft> unique = new LinkedHashMap<>();
        drafts.stream().filter(draft -> draft.recipientUserId() != null).forEach(draft -> unique.putIfAbsent(
                new DedupKey(draft.recipientUserId(), draft.type(), draft.referenceType(), draft.referenceId()), draft));
        if (unique.isEmpty()) {
            return 0;
        }
        List<NotificationDraft> batch = List.copyOf(unique.values());
        List<InsertedNotificationView> rows = notificationRepository.insertAllReturning(
                batch.stream().map(NotificationDraft::recipientUserId).toArray(UUID[]::new),
                batch.stream().map(draft -> draft.type().name()).toArray(String[]::new),
                batch.stream().map(NotificationDraft::title).toArray(String[]::new),
                batch.stream().map(NotificationDraft::body).toArray(String[]::new),
                batch.stream().map(draft -> draft.referenceType() == null ? null : draft.referenceType().name())
                        .toArray(String[]::new),
                batch.stream().map(NotificationDraft::referenceId).toArray(UUID[]::new),
                now);
        log.info("Notifications written: types={}, rows={}",
                batch.stream().map(NotificationDraft::type).distinct().toList(), rows.size());
        if (rows.isEmpty()) {
            return 0;
        }
        userRealtimeGateway.publishAfterCommit(rows.stream()
                .map(row -> new UserFrameDelivery(row.getRecipientUserId(),
                        new UserFrame.NotificationCreated(notificationMapper.toResponse(row))))
                .toList());
        registerPushAfterCommit(rows);
        return rows.size();
    }

    private void registerPushAfterCommit(List<InsertedNotificationView> rows) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rows.forEach(NotificationWriter.this::pushSafely);
            }
        });
    }

    /** Push lỗi không ảnh hưởng thông báo in-app đã lưu; chỉ log loại + người nhận. */
    private void pushSafely(InsertedNotificationView row) {
        Notification.Type type = Notification.Type.valueOf(row.getType());
        try {
            pushSender.send(row.getRecipientUserId(), new PushMessage(type, row.getTitle(), row.getBody(),
                    row.getReferenceType() == null ? null : Notification.ReferenceType.valueOf(row.getReferenceType()),
                    row.getReferenceId()));
        } catch (RuntimeException exception) {
            log.warn("Push failed: type={}, recipient={}", type, row.getRecipientUserId(), exception);
        }
    }
}
