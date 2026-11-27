package com.phananh.arenaflow.application.listener;

import com.phananh.arenaflow.application.event.match.MatchArchivedEvent;
import com.phananh.arenaflow.application.event.match.MatchCancelledEvent;
import com.phananh.arenaflow.application.event.match.MatchConfirmedEvent;
import com.phananh.arenaflow.application.event.match.MatchExpiredEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestRejectedEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestSentEvent;
import com.phananh.arenaflow.application.event.match.MatchRequestWithdrawnEvent;
import com.phananh.arenaflow.application.event.match.PairingCreatedEvent;
import com.phananh.arenaflow.application.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Event của kèo (luồng A và B) → thông báo. Chạy trên {@code notificationExecutor} sau khi transaction phát event commit (lần thử bị rollback
 * không tới đây). Không mang transaction: dispatcher tự mở transaction mới. Lỗi chỉ log, không thử lại, không ném — thao
 * tác nghiệp vụ đã commit không bị ảnh hưởng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchNotificationListener {

    private final NotificationDispatcher dispatcher;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchRequestSentEvent event) {
        dispatchSafely("MatchRequestSentEvent", event.requestId(), () -> dispatcher.matchRequestSent(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchRequestRejectedEvent event) {
        dispatchSafely("MatchRequestRejectedEvent", event.requestId(), () -> dispatcher.matchRequestRejected(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchRequestWithdrawnEvent event) {
        dispatchSafely("MatchRequestWithdrawnEvent", event.requestId(), () -> dispatcher.matchRequestWithdrawn(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchConfirmedEvent event) {
        dispatchSafely("MatchConfirmedEvent", event.matchId(), () -> dispatcher.matchConfirmed(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(PairingCreatedEvent event) {
        dispatchSafely("PairingCreatedEvent", event.pairingId(), () -> dispatcher.pairingCreated(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchCancelledEvent event) {
        dispatchSafely("MatchCancelledEvent", event.matchId(), () -> dispatcher.matchCancelled(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchExpiredEvent event) {
        dispatchSafely("MatchExpiredEvent", event.matchId(), () -> dispatcher.matchExpired(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchArchivedEvent event) {
        dispatchSafely("MatchArchivedEvent", event.matchId(), () -> dispatcher.matchArchived(event));
    }

    private void dispatchSafely(String eventName, UUID id, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (RuntimeException exception) {
            log.error("Notification dispatch failed: event={}, id={}", eventName, id, exception);
        }
    }
}
