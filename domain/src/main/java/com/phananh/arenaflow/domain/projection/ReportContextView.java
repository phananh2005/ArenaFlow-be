package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ngữ cảnh gửi report sau trận: trận, trạng thái hai đội, quyền đội trưởng của người gọi, mỗi phía đã report phía kia
 * loại đang gửi chưa; tên / ảnh hai đội để dựng response không đọc lại.
 */
public interface ReportContextView extends MatchSidesView {

    UUID getMatchId();

    String getStatus();

    LocalDateTime getMatchStartTime();

    String getAuthorTeamName();

    String getAuthorTeamAvatarUrl();

    String getOpponentTeamName();

    String getOpponentTeamAvatarUrl();

    Boolean getAuthorReportedType();

    Boolean getOpponentReportedType();
}
