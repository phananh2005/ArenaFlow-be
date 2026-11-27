package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExternalMatchCancellationResponse(UUID externalMatchId, String status, LocalDateTime cancelledAt,
                                                int closedRecruitmentCount, int rejectedApplicationCount) {
}
