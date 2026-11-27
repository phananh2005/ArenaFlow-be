package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record SportResponse(UUID sportId, String sportName, Boolean isActive, LocalDateTime createdAt) {
}
