package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tin nhắn; bên gửi khớp một phần tử {@code parties} của đầu phòng chat, người gửi đã xoá được che. */
public record ConversationMessageResponse(UUID messageId, UUID conversationId, String senderPartyType,
                                          UUID senderPartyId, UUID senderUserId, String senderName,
                                          String senderAvatarUrl, String content, boolean isRead,
                                          LocalDateTime createdAt) {
}
