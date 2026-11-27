package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.projection.ConversationContextView;

import java.time.LocalDateTime;
import java.util.UUID;

/** Quy tắc quyền của một loại ngữ cảnh cuộc trò chuyện; một bean mỗi loại. */
public interface ConversationAccessPolicy {

    Conversation.ContextType contextType();

    ConversationAccess resolve(ConversationContextView ctx, UUID callerId, LocalDateTime now);
}
