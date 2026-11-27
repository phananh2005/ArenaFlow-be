package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đánh giá mercenary dành cho đội (chỉ khi đội đã đánh giá lại trong cùng đơn). */
public interface TeamMercenaryRatingView {
    UUID getRatingId();
    UUID getMatchId();
    UUID getExternalMatchId();
    LocalDateTime getGameStart();
    UUID getUserId();
    String getDisplayName();
    String getAvatarUrl();
    Boolean getDeleted();
    Integer getScore();
    String getComment();
    LocalDateTime getCreatedAt();
}
