package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Cờ người gọi đã xoá và mốc con trỏ (null khi mốc không phải thông báo của người gọi trong hạn hiển thị). */
public interface NotificationCallerView {
    Boolean getCallerDeleted();
    LocalDateTime getAnchorCreatedAt();
    UUID getAnchorId();
}
