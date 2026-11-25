package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Vị trí con trỏ phân trang tin nhắn: {@code (createdAt, id)}. */
public interface ChatCursorView {
    LocalDateTime getCreatedAt();
    UUID getId();
}
