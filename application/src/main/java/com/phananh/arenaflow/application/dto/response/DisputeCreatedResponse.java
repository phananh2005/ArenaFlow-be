package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeCreatedResponse(UUID disputeId, UUID reportId, String status, String evidence, String reportStatus,
                                     LocalDateTime createdAt) {
}
