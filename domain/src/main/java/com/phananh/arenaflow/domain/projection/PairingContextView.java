package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ngữ cảnh ghi của một thương lượng trong một câu: pairing + kèo (kèm {@code version}) + trạng thái hai đội + bộ môn
 * + khung giờ và trạng thái địa điểm của phương án + cuộc trò chuyện + cờ người gọi là đội trưởng ACTIVE từng phía.
 */
public interface PairingContextView {
    UUID getId();
    UUID getMatchId();
    UUID getInterestedTeamId();
    /** Trạng thái lưu trong DB (không tính hết hạn). */
    String getStatus();
    Integer getRevision();
    LocalDateTime getAuthorConfirmedAt();
    LocalDateTime getInterestedConfirmedAt();
    UUID getProposedVenueId();
    UUID getProposedTimeOptionId();
    String getCostType();
    String getCostSplit();
    Long getCostVenueAmount();
    Long getCostDrinksAmount();
    Long getCostDepositAmount();
    String getCostDepositHolder();
    LocalDateTime getUpdatedAt();
    UUID getAuthorTeamId();
    String getMatchStatus();
    LocalDateTime getExpiresAt();
    String getMatchType();
    Long getMatchVersion();
    String getAuthorTeamStatus();
    String getInterestedTeamStatus();
    Boolean getSportActive();
    LocalDateTime getProposedStartTime();
    LocalDateTime getProposedEndTime();
    String getProposedVenueStatus();
    UUID getConversationId();
    Boolean getCallerAuthorCaptain();
    Boolean getCallerInterestedCaptain();
}
