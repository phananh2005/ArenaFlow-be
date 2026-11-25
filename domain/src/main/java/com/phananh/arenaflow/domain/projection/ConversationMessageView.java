package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tin nhắn kèm hồ sơ công khai của người gửi (chưa che — mapper che người đã xoá). */
public interface ConversationMessageView {
    UUID getId();
    UUID getConversationId();
    String getSenderParty();
    UUID getSenderUserId();
    String getSenderName();
    String getSenderAvatarUrl();
    Boolean getSenderDeleted();
    String getMessage();
    Boolean getIsRead();
    LocalDateTime getCreatedAt();
}
