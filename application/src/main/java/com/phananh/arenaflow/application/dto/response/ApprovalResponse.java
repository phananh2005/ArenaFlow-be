package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record ApprovalResponse(UUID applicationId, String status, LocalDateTime respondedAt,
                               RecruitmentCountResponse recruitment) {
}
