package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;

/** Frame gửi trên topic của một cuộc trò chuyện; loại khác (thương lượng, đơn ứng tuyển) do mã sau thêm. */
public sealed interface RealtimeFrame {

    /** Có tin mới trong cuộc trò chuyện (kể cả tin của chính người nhận). */
    record MessageCreated(ConversationMessageResponse message) implements RealtimeFrame {
    }
}
