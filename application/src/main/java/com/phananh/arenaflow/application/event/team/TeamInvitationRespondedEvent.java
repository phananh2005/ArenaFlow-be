package com.phananh.arenaflow.application.event.team;

import java.time.LocalDateTime;
import java.util.UUID;

/** Người được mời chấp nhận ({@code accepted = true}) hoặc từ chối lời mời. */
public record TeamInvitationRespondedEvent(UUID invitationId, UUID teamId, boolean accepted, LocalDateTime occurredAt) {
}
