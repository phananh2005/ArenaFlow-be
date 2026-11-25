package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu ghép kèo cho danh sách; {@code effectiveStatus}: PENDING của kèo đã quá hạn → EXPIRED. Ngữ cảnh kèo
 * ({@code startsAt}, đội tạo kèo) chỉ có ở danh sách theo đội gửi.
 */
public interface MatchRequestView {
    UUID getRequestId();
    UUID getMatchId();
    UUID getConversationId();
    UUID getRequesterTeamId();
    String getRequesterTeamName();
    Integer getRequesterTeamReliabilityScore();
    String getEffectiveStatus();
    String getReason();
    LocalDateTime getRequestedAt();
    LocalDateTime getRespondedAt();
    LocalDateTime getWithdrawnAt();
    LocalDateTime getStartsAt();
    UUID getAuthorTeamId();
    String getAuthorTeamName();
}
