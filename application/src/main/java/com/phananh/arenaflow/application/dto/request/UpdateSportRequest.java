package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateSportRequest(@NotNull Boolean isActive) {
}
