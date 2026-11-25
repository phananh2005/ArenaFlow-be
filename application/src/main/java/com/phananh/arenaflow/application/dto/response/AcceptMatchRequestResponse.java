package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record AcceptMatchRequestResponse(UUID matchId, String matchStatus, UUID requestId, String requestStatus,
                                         UUID opponentTeamId, String opponentTeamName, LocalDateTime respondedAt) {
}
