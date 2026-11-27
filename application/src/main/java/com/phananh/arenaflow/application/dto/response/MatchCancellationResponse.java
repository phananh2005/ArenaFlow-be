package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchCancellationResponse(UUID matchId, String status, UUID cancelledByTeamId, LocalDateTime cancelledAt,
                                        String cancelReason, int reliabilityPenalty) {
}
