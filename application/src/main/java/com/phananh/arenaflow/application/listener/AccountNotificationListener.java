package com.phananh.arenaflow.application.listener;

import com.phananh.arenaflow.application.event.team.CaptainTransferredEvent;
import com.phananh.arenaflow.application.event.team.TeamInvitationCreatedEvent;
import com.phananh.arenaflow.application.event.team.TeamInvitationRespondedEvent;
import com.phananh.arenaflow.application.event.team.TeamStatusChangedByAdminEvent;
import com.phananh.arenaflow.application.event.user.UserRegisteredEvent;
import com.phananh.arenaflow.application.event.venue.VenueAdminAlertEvent;
import com.phananh.arenaflow.application.service.NotificationDispatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Event của tài khoản, đội và địa điểm → thông báo. Chạy trên {@code notificationExecutor} sau khi transaction phát event commit (lần thử bị rollback
 * không tới đây). Không mang transaction: dispatcher tự mở transaction mới. Lỗi chỉ log, không thử lại, không ném — thao
 * tác nghiệp vụ đã commit không bị ảnh hưởng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountNotificationListener {

    private final NotificationDispatcher dispatcher;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(UserRegisteredEvent event) {
        dispatchSafely("UserRegisteredEvent", event.userId(), () -> dispatcher.userRegistered(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TeamInvitationCreatedEvent event) {
        dispatchSafely("TeamInvitationCreatedEvent", event.invitationId(), () -> dispatcher.teamInvitationCreated(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TeamInvitationRespondedEvent event) {
        dispatchSafely("TeamInvitationRespondedEvent", event.invitationId(), () -> dispatcher.teamInvitationResponded(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(CaptainTransferredEvent event) {
        dispatchSafely("CaptainTransferredEvent", event.teamId(), () -> dispatcher.captainTransferred(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(TeamStatusChangedByAdminEvent event) {
        dispatchSafely("TeamStatusChangedByAdminEvent", event.teamId(), () -> dispatcher.teamStatusChangedByAdmin(event));
    }

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(VenueAdminAlertEvent event) {
        dispatchSafely("VenueAdminAlertEvent", event.venueId(), () -> dispatcher.venueAdminAlert(event));
    }

    private void dispatchSafely(String eventName, UUID id, Runnable dispatch) {
        try {
            dispatch.run();
        } catch (RuntimeException exception) {
            log.error("Notification dispatch failed: event={}, id={}", eventName, id, exception);
        }
    }
}
