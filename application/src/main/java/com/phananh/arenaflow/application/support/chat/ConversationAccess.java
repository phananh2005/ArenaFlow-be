package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;

/**
 * Quyền của người gọi trên một cuộc trò chuyện.
 *
 * @param viewerParty    bên của người gọi; null = không tham gia (CHAT_003)
 * @param canSend        gửi tin được ngay lúc này
 * @param expiryDue      gửi tin phải chuyển kèo sang EXPIRED trước (yêu cầu còn chờ trên kèo đã tới hạn)
 * @param viewerReadOnly người gọi là người đã giải tán đội của mình — chỉ xem, không kích hoạt chuyển EXPIRED
 */
public record ConversationAccess(Conversation.Party viewerParty, boolean canSend, boolean expiryDue,
                                 boolean viewerReadOnly) {

    public static final ConversationAccess NONE = new ConversationAccess(null, false, false, false);
}
