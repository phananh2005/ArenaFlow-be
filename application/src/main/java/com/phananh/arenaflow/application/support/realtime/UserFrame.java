package com.phananh.arenaflow.application.support.realtime;

import com.phananh.arenaflow.application.dto.response.NotificationResponse;

import java.time.LocalDateTime;
import java.util.UUID;

/** Frame gửi tới kênh cá nhân của một người dùng; dựng từ dữ liệu đã có trong bộ nhớ, không đọc lại DB. */
public sealed interface UserFrame {

    record NotificationCreated(NotificationResponse notification) implements UserFrame {
    }

    record ConversationActivity(UUID conversationId, UUID messageId, LocalDateTime createdAt) implements UserFrame {
    }
}
