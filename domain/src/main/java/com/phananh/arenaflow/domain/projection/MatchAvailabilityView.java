package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/**
 * Điều kiện nhận yêu cầu của kèo (trạng thái đội tạo kèo, bộ môn đang bật), dữ liệu hiển thị đội tạo kèo và cuộc trò
 * chuyện đã có của cặp (kèo, đội gửi) — một câu.
 */
public interface MatchAvailabilityView {
    String getAuthorTeamStatus();
    Boolean getSportActive();
    String getAuthorTeamName();
    String getAuthorTeamAvatarUrl();
    Integer getAuthorTeamReliabilityScore();
    String getAuthorCaptainName();
    Boolean getAuthorCaptainDeleted();
    UUID getExistingConversationId();
}
