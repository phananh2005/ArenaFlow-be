package com.phananh.arenaflow.api.realtime;

import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Đẩy frame lên topic {@code /topic/conversations/{id}} sau khi transaction commit (lần thử rollback không đẩy). Lỗi
 * gửi chỉ log WARN (mã cuộc trò chuyện + loại frame, không nội dung) — dữ liệu đã commit, client bù bằng lịch sử.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompChatRealtimeGateway implements ChatRealtimeGateway {

    static final String TOPIC_PREFIX = "/topic/conversations/";

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void publishAfterCommit(UUID conversationId, RealtimeFrame frame) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("ChatRealtimeGateway requires an active transaction");
        }
        String type = frameType(frame);
        Map<String, Object> payload = payload(frame);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    messagingTemplate.convertAndSend(TOPIC_PREFIX + conversationId, (Object) payload);
                } catch (RuntimeException exception) {
                    log.warn("Realtime frame {} for conversation {} not delivered: {}", type, conversationId,
                            exception.getClass().getSimpleName());
                }
            }
        });
    }

    private static String frameType(RealtimeFrame frame) {
        return switch (frame) {
            case RealtimeFrame.MessageCreated ignored -> "MESSAGE_CREATED";
            case RealtimeFrame.PairingUpdated ignored -> "PAIRING_UPDATED";
        };
    }

    private static Map<String, Object> payload(RealtimeFrame frame) {
        return switch (frame) {
            case RealtimeFrame.MessageCreated created -> Map.of("type", frameType(frame), "message", created.message());
            case RealtimeFrame.PairingUpdated updated -> pairingPayload(updated);
        };
    }

    /** Frame thương lượng: các mốc xác nhận có thể {@code null} nên dùng map chấp nhận giá trị null, giữ thứ tự field. */
    private static Map<String, Object> pairingPayload(RealtimeFrame.PairingUpdated updated) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", frameType(updated));
        payload.put("pairingId", updated.pairingId());
        payload.put("status", updated.status().name());
        payload.put("revision", updated.revision());
        payload.put("authorConfirmedAt", updated.authorConfirmedAt());
        payload.put("interestedConfirmedAt", updated.interestedConfirmedAt());
        payload.put("confirmedAt", updated.confirmedAt());
        payload.put("change", updated.change().name());
        return payload;
    }
}
