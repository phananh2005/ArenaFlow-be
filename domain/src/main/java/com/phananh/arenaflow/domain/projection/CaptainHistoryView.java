package com.phananh.arenaflow.domain.projection;

import java.time.Instant;
import java.util.UUID;

/** Lịch sử chuyển quyền captain; fromUserId null = lần tạo đội. Cờ *Deleted → mapper che tên. */
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
    Instant getTransferredAt();
}
