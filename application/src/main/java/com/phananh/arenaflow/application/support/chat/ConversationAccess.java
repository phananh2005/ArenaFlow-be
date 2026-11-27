package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;

/**
 * Quyền của người gọi với một cuộc trò chuyện: {@code viewerParty} null = không tham gia; {@code expiryDue} = gửi tin
 * phải chuyển kèo hết hạn trước; {@code viewerReadOnly} = người đã giải tán đội của mình (chỉ xem).
 */
public record ConversationAccess(Conversation.Party viewerParty, boolean canSend, boolean expiryDue,
                                 boolean viewerReadOnly) {
}
