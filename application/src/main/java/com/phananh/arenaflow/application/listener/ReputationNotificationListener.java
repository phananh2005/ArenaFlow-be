package com.phananh.arenaflow.application.listener;

import com.phananh.arenaflow.application.event.reputation.DisputeResolvedEvent;
import com.phananh.arenaflow.application.event.reputation.MatchRatedEvent;
import com.phananh.arenaflow.application.event.reputation.ReportDisputedEvent;
import com.phananh.arenaflow.application.event.reputation.TeamReportedEvent;
import com.phananh.arenaflow.application.event.reputation.TeamVerificationChangedEvent;
import com.phananh.arenaflow.application.event.reputation.UserVerificationChangedEvent;
import com.phananh.arenaflow.application.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Event của uy tín: đánh giá, report, tranh chấp, huy hiệu → thông báo. Chạy trên {@code notificationExecutor} sau khi transaction phát event commit (lần thử bị rollback
 * không tới đây). Không mang transaction: dispatcher tự mở transaction mới. Lỗi chỉ log, không thử lại, không ném — thao
 * tác nghiệp vụ đã commit không bị ảnh hưởng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReputationNotificationListener {

    private final NotificationDispatcher dispatcher;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MatchRatedEvent event) {
        dispatchSafely("MatchRatedEvent", event.ratingId(), () -> dispatcher.matchRated(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TeamReportedEvent event) {
        dispatchSafely("TeamReportedEvent", event.reportId(), () -> dispatcher.teamReported(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ReportDisputedEvent event) {
        dispatchSafely("ReportDisputedEvent", event.disputeId(), () -> dispatcher.reportDisputed(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DisputeResolvedEvent event) {
        dispatchSafely("DisputeResolvedEvent", event.disputeId(), () -> dispatcher.disputeResolved(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TeamVerificationChangedEvent event) {
        dispatchSafely("TeamVerificationChangedEvent", event.teamId(), () -> dispatcher.teamVerificationChanged(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserVerificationChangedEvent event) {
        dispatchSafely("UserVerificationChangedEvent", event.userId(), () -> dispatcher.userVerificationChanged(event));
    }

    private void dispatchSafely(String eventName, UUID id, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (RuntimeException exception) {
            log.error("Notification dispatch failed: event={}, id={}", eventName, id, exception);
        }
    }
}
