package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chi tiết thương lượng trong một câu: trạng thái hiệu lực, hai đội (kèm đội trưởng hiện tại, cờ đã xoá tài khoản),
 * phương án đã giải tên địa điểm / giờ, thoả thuận chi phí + cọc, cuộc trò chuyện, cờ quyền của người gọi.
 */
public interface PairingDetailView {
    UUID getPairingId();
    UUID getMatchId();
    UUID getConversationId();
    String getEffectiveStatus();
    Integer getRevision();
    LocalDateTime getMatchExpiresAt();
    UUID getAuthorTeamId();
    String getAuthorTeamName();
    String getAuthorTeamAvatarUrl();
    Integer getAuthorTeamReliabilityScore();
    String getAuthorCaptainName();
    Boolean getAuthorCaptainDeleted();
    UUID getInterestedTeamId();
    String getInterestedTeamName();
    String getInterestedTeamAvatarUrl();
    Integer getInterestedTeamReliabilityScore();
    String getInterestedCaptainName();
    Boolean getInterestedCaptainDeleted();
    UUID getProposedVenueId();
    String getProposedVenueName();
    String getProposedVenueAddress();
    String getProposedVenueStatus();
    UUID getProposedTimeOptionId();
    LocalDateTime getProposedStartTime();
    LocalDateTime getProposedEndTime();
    LocalDateTime getAuthorConfirmedAt();
    LocalDateTime getInterestedConfirmedAt();
    LocalDateTime getFinalConfirmedAt();
    String getCostType();
    String getCostSplit();
    Long getCostVenueAmount();
    Long getCostDrinksAmount();
    Long getCostDepositAmount();
    String getCostDepositHolder();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
    Boolean getCallerAuthorCaptain();
    Boolean getCallerInterestedCaptain();
}
