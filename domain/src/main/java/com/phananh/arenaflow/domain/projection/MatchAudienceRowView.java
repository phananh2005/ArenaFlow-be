package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một đội nhận thông báo của một kèo (đội liệt kê / yêu cầu / thương lượng) kèm dữ liệu hiển thị. */
public interface MatchAudienceRowView {
    UUID getMatchId();
    String getMatchType();
    String getMatchStatus();
    LocalDateTime getMatchStartTime();
    String getVenueName();
    UUID getAuthorTeamId();
    String getAuthorTeamName();
    UUID getOpponentTeamId();
    String getSource();
    UUID getTeamId();
    String getTeamName();
    UUID getCaptainUserId();
    Boolean getHasRated();
}
