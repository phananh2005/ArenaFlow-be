package com.phananh.ArenaFlow.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** §3.15. */
public record InvitationResponse(UUID invitationId, UUID teamId, String teamName, UUID userId, String status,
                                 OffsetDateTime invitedAt) {
}
