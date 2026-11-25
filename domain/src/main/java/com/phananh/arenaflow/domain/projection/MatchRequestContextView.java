package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Yêu cầu + kèo + đội gửi + cờ bộ môn đang bật, đọc trong một câu cho chấp nhận / từ chối / rút. */
public interface MatchRequestContextView {
    UUID getRequestId();
    UUID getMatchId();
    UUID getRequesterTeamId();
    String getStatus();
    UUID getAuthorTeamId();
    String getMatchStatus();
    String getMatchType();
    LocalDateTime getExpiresAt();
    LocalDateTime getMatchStartTime();
    String getRequesterTeamName();
    String getRequesterTeamStatus();
    Integer getRequesterTeamReliabilityScore();
    Boolean getSportActive();
}
