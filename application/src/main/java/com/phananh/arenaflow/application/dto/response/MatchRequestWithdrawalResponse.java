package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchRequestWithdrawalResponse(UUID requestId, UUID matchId, String status, LocalDateTime withdrawnAt,
                                             String matchStatus, int reliabilityPenalty) {
}
