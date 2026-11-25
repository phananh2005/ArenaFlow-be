package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.domain.entity.match.Pairing;

import java.time.LocalDateTime;
import java.util.UUID;

/** Frame gửi trên topic của một cuộc trò chuyện; dữ liệu dựng từ bộ nhớ (không đọc lại DB). */
public sealed interface RealtimeFrame {

    /** Có tin mới trong cuộc trò chuyện (kể cả tin của chính người nhận). */
    record MessageCreated(ConversationMessageResponse message) implements RealtimeFrame {
    }

    /** Gói thương lượng / xác nhận của pairing vừa đổi. */
    record PairingUpdated(UUID pairingId, Pairing.Status status, int revision, LocalDateTime authorConfirmedAt,
                          LocalDateTime interestedConfirmedAt, LocalDateTime confirmedAt, PairingChange change)
            implements RealtimeFrame {
    }
}
