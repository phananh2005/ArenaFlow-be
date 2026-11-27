package com.phananh.arenaflow.application.event.team;

import com.phananh.arenaflow.domain.entity.team.Team;
import java.time.LocalDateTime;
import java.util.UUID;

/** Admin tạm ngưng ({@code INACTIVE}) hoặc kích hoạt lại ({@code ACTIVE}) đội. */
public record TeamStatusChangedByAdminEvent(UUID teamId, Team.TeamStatus status, LocalDateTime occurredAt) {
}
