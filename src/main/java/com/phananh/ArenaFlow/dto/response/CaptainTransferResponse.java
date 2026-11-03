package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** §2.15. */
public record CaptainTransferResponse(UUID teamId, UUID fromUserId, UUID toUserId, String reason,
                                      OffsetDateTime transferredAt) {
}
