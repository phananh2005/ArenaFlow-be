package com.phananh.arenaflow.application.support.chat;

import java.util.UUID;

/** Cổng đẩy frame realtime tới topic của cuộc trò chuyện. */
public interface ChatRealtimeGateway {

    /** Phải gọi trong transaction đang chạy: đăng ký đẩy sau commit; lần thử rollback không đẩy. */
    void publishAfterCommit(UUID conversationId, RealtimeFrame frame);
}
