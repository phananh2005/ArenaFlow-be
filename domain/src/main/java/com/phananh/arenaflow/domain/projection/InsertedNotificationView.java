package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Dòng thông báo vừa ghi (RETURNING) — dựng frame realtime không cần đọc lại. */
public interface InsertedNotificationView {
    UUID getId();
    UUID getRecipientUserId();
    String getType();
    String getTitle();
    String getBody();
    String getReferenceType();
    UUID getReferenceId();
    LocalDateTime getCreatedAt();
}
