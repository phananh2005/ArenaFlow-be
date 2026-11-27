package com.phananh.arenaflow.application.support.notification;

import com.phananh.arenaflow.application.support.realtime.NoOpUserRealtimeGateway;
import com.phananh.arenaflow.domain.entity.chat.Notification;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoOpChannelsTest {

    @Test
    void noOpPushSenderNeverThrows() {
        assertThatCode(() -> new NoOpPushSender().send(UUID.randomUUID(),
                new PushMessage(Notification.Type.WELCOME, "t", "b", null, null))).doesNotThrowAnyException();
    }

    @Test
    void noOpUserGatewayStillRequiresActiveTransaction() {
        NoOpUserRealtimeGateway gateway = new NoOpUserRealtimeGateway();

        assertThatThrownBy(() -> gateway.publishAfterCommit(List.of())).isInstanceOf(IllegalStateException.class);

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatCode(() -> gateway.publishAfterCommit(List.of())).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
