package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Yêu cầu ghép kèo; {@code status} là trạng thái hiển thị. {@code startsAt}, {@code authorTeamId},
 * {@code authorTeamName} chỉ có ở danh sách yêu cầu của đội gửi.
 */
public record MatchRequestResponse(UUID requestId, UUID matchId, UUID conversationId, UUID requesterTeamId,
                                   String requesterTeamName, Integer requesterTeamReliabilityScore, String status,
                                   String reason, LocalDateTime requestedAt, LocalDateTime respondedAt,
                                   LocalDateTime withdrawnAt, LocalDateTime startsAt, UUID authorTeamId,
                                   String authorTeamName) {
}
