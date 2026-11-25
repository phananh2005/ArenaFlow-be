package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.application.support.chat.PairingChange;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(OutputCaptureExtension.class)
class StompChatRealtimeGatewayTest {

    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    private final StompChatRealtimeGateway gateway = new StompChatRealtimeGateway(template);
    private final UUID conversationId = UUID.randomUUID();
    private final ConversationMessageResponse message = new ConversationMessageResponse(UUID.randomUUID(),
            conversationId, "TEAM", UUID.randomUUID(), UUID.randomUUID(), "Nam", null, "Bí mật", false,
            LocalDateTime.of(2026, 11, 21, 10, 0));

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void sendsMessageCreatedFrameOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        gateway.publishAfterCommit(conversationId, new RealtimeFrame.MessageCreated(message));
        verifyNoInteractions(template);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(template).convertAndSend("/topic/conversations/" + conversationId,
                (Object) Map.of("type", "MESSAGE_CREATED", "message", message));
    }

    @Test
    void sendsPairingUpdatedFrameWithNullConfirmationsAfterCommit() {
        UUID pairingId = UUID.randomUUID();
        LocalDateTime authorAt = LocalDateTime.of(2026, 11, 10, 15, 10);
        TransactionSynchronizationManager.initSynchronization();

        gateway.publishAfterCommit(conversationId, new RealtimeFrame.PairingUpdated(pairingId,
                Pairing.Status.NEGOTIATING, 3, authorAt, null, null, PairingChange.CONFIRMATION));
        verifyNoInteractions(template);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("type", "PAIRING_UPDATED");
        expected.put("pairingId", pairingId);
        expected.put("status", "NEGOTIATING");
        expected.put("revision", 3);
        expected.put("authorConfirmedAt", authorAt);
        expected.put("interestedConfirmedAt", null);
        expected.put("confirmedAt", null);
        expected.put("change", "CONFIRMATION");
        verify(template).convertAndSend("/topic/conversations/" + conversationId, (Object) expected);
    }

    @Test
    void rolledBackAttemptSendsNothing() {
        TransactionSynchronizationManager.initSynchronization();

        gateway.publishAfterCommit(conversationId, new RealtimeFrame.MessageCreated(message));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(template);
    }

    @Test
    void requiresActiveTransaction() {
        assertThatThrownBy(() -> gateway.publishAfterCommit(conversationId, new RealtimeFrame.MessageCreated(message)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sendFailureIsLoggedWithoutContentAndNotRethrown(CapturedOutput output) {
        doThrow(new MessagingException("broker down")).when(template).convertAndSend(anyString(), any(Object.class));
        TransactionSynchronizationManager.initSynchronization();
        gateway.publishAfterCommit(conversationId, new RealtimeFrame.MessageCreated(message));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(output.getAll()).contains(conversationId.toString()).contains("MESSAGE_CREATED")
                .doesNotContain("Bí mật");
    }
}
