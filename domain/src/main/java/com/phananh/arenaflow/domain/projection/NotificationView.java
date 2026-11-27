package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một thông báo trong danh sách của người nhận. */
public interface NotificationView {
    UUID getId();
    String getType();
    String getTitle();
    String getBody();
    String getReferenceType();
    UUID getReferenceId();
    Boolean getIsRead();
    LocalDateTime getCreatedAt();
}
