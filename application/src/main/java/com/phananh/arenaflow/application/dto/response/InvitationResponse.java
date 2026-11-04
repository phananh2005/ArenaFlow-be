package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Lời mời vào đội (PENDING / ACTIVE / REJECTED). */
public record InvitationResponse(UUID invitationId, UUID teamId, String teamName, UUID userId, String status,
                                 OffsetDateTime invitedAt) {
}
