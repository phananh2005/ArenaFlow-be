package com.phananh.arenaflow.application.event.team;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội trưởng mời một người vào đội. */
public record TeamInvitationCreatedEvent(UUID invitationId, UUID teamId, UUID inviteeUserId, LocalDateTime occurredAt) {
}
