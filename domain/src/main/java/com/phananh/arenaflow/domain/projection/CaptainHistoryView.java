package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lịch sử chuyển quyền captain; fromUserId null = lần tạo đội. Cờ *Deleted → mapper che tên.
 * Cột thời điểm ({@code transferredAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code LocalDateTime}.
 */
public interface CaptainHistoryView {
    UUID getHistoryId();
    UUID getTeamId();
    UUID getFromUserId();
    String getFromUserName();
    Boolean getFromUserDeleted();
    UUID getToUserId();
    String getToUserName();
    Boolean getToUserDeleted();
    String getReason();
    LocalDateTime getTransferredAt();
}
