package com.phananh.arenaflow.application.event.team;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đội trưởng chuyển quyền cho thành viên khác. */
public record CaptainTransferredEvent(UUID teamId, UUID newCaptainUserId, UUID previousCaptainUserId,
                                      LocalDateTime occurredAt) {
}
