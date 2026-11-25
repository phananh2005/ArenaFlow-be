package com.phananh.arenaflow.application.support.chat;

import java.util.UUID;

/** Cổng đẩy frame realtime lên topic của một cuộc trò chuyện. */
public interface ChatRealtimeGateway {

    /**
     * Đăng ký gửi {@code frame} sau khi transaction hiện tại commit (bắt buộc đang trong transaction): lần thử bị
     * rollback không đẩy; lần thử commit đẩy đúng một lần.
     */
    void publishAfterCommit(UUID conversationId, RealtimeFrame frame);
}
