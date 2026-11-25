package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.projection.ConversationContextView;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quy tắc quyền của một loại ngữ cảnh cuộc trò chuyện — một bean mỗi loại; thiếu bean cho một loại → lỗi khởi động.
 * Tính trong bộ nhớ từ ngữ cảnh đã đọc, không truy vấn.
 */
public interface ConversationAccessPolicy {

    Conversation.ContextType contextType();

    ConversationAccess resolve(ConversationContextView context, UUID callerId, LocalDateTime now);
}
