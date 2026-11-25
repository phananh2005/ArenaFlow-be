package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đội tác giả (trạng thái, tên/ảnh/điểm, đội trưởng hiện tại) + bộ môn đang bật + cuộc trò chuyện đã có — một câu. */
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
