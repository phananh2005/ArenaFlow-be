package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một dòng danh sách thương lượng (của kèo / của đội), trạng thái hiệu lực tính trong SQL. */
public interface PairingSummaryView {
    UUID getPairingId();
    UUID getMatchId();
    UUID getConversationId();
    String getEffectiveStatus();
    Integer getRevision();
    UUID getAuthorTeamId();
    String getAuthorTeamName();
    UUID getInterestedTeamId();
    String getInterestedTeamName();
    Integer getInterestedTeamReliabilityScore();
    UUID getProposedVenueId();
    UUID getProposedTimeOptionId();
    LocalDateTime getAuthorConfirmedAt();
    LocalDateTime getInterestedConfirmedAt();
    LocalDateTime getFinalConfirmedAt();
    LocalDateTime getMatchStartTime();
    LocalDateTime getMatchExpiresAt();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
