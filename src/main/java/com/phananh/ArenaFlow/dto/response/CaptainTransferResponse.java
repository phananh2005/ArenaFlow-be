package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Kết quả chuyển quyền đội trưởng. */
public record CaptainTransferResponse(UUID teamId, UUID fromUserId, UUID toUserId, String reason,
                                      OffsetDateTime transferredAt) {
}
