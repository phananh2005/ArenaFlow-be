package com.phananh.arenaflow.application.listener;

import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationApprovedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationRejectedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationSubmittedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryApplicationWithdrawnEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryGameCancelledEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRatingCreatedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryRecruitmentClosedEvent;
import com.phananh.arenaflow.application.event.mercenary.MercenaryReportCreatedEvent;
import com.phananh.arenaflow.application.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Event của tuyển mercenary → thông báo. Chạy trên {@code notificationExecutor} sau khi transaction phát event commit (lần thử bị rollback
 * không tới đây). Không mang transaction: dispatcher tự mở transaction mới. Lỗi chỉ log, không thử lại, không ném — thao
 * tác nghiệp vụ đã commit không bị ảnh hưởng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MercenaryNotificationListener {

    private final NotificationDispatcher dispatcher;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryApplicationSubmittedEvent event) {
        dispatchSafely("MercenaryApplicationSubmittedEvent", event.applicationId(), () -> dispatcher.mercenaryApplicationSubmitted(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryApplicationApprovedEvent event) {
        dispatchSafely("MercenaryApplicationApprovedEvent", event.applicationId(), () -> dispatcher.mercenaryApplicationApproved(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryApplicationRejectedEvent event) {
        dispatchSafely("MercenaryApplicationRejectedEvent", event.applicationId(), () -> dispatcher.mercenaryApplicationRejected(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryApplicationWithdrawnEvent event) {
        dispatchSafely("MercenaryApplicationWithdrawnEvent", event.applicationId(), () -> dispatcher.mercenaryApplicationWithdrawn(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryRecruitmentClosedEvent event) {
        dispatchSafely("MercenaryRecruitmentClosedEvent", event.recruitmentId(), () -> dispatcher.mercenaryRecruitmentClosed(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryGameCancelledEvent event) {
        dispatchSafely("MercenaryGameCancelledEvent", event.matchId(), () -> dispatcher.mercenaryGameCancelled(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryRatingCreatedEvent event) {
        dispatchSafely("MercenaryRatingCreatedEvent", event.ratingId(), () -> dispatcher.mercenaryRatingCreated(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MercenaryReportCreatedEvent event) {
        dispatchSafely("MercenaryReportCreatedEvent", event.reportId(), () -> dispatcher.mercenaryReportCreated(event));
    }

    private void dispatchSafely(String eventName, UUID id, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (RuntimeException exception) {
            log.error("Notification dispatch failed: event={}, id={}", eventName, id, exception);
        }
    }
}
