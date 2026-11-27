package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Người nhận nhắc của một trận: đội trưởng (CAPTAIN) hoặc mercenary đã duyệt (MERCENARY). */
public interface GameAudienceRowView {
    String getKind();
    UUID getRecipientUserId();
    UUID getMatchId();
    UUID getExternalMatchId();
    UUID getApplicationId();
    LocalDateTime getStartTime();
    String getVenueName();
    String getTeamName();
    String getOpponentTeamName();
    String getPosition();
}
