package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Lời mời đang chờ gửi tới người gọi. */
public record MyInvitationResponse(UUID invitationId, LocalDateTime invitedAt, MyInvitationTeamResponse team) {
}
