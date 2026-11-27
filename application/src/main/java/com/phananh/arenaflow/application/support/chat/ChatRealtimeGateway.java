package com.phananh.arenaflow.application.support.chat;

import java.util.UUID;

/** Cổng đẩy frame realtime theo cuộc trò chuyện — tầng application không phụ thuộc thư viện WebSocket. */
public interface ChatRealtimeGateway {

    /**
     * Bắt buộc trong transaction đang chạy: chỉ đẩy sau khi transaction commit (lần thử rollback không đẩy). Frame dựng
     * từ dữ liệu trong bộ nhớ, không đọc lại DB.
     */
    void publishAfterCommit(UUID conversationId, RealtimeFrame frame);
}
