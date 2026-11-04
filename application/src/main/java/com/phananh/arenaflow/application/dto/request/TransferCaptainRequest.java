package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record TransferCaptainRequest(@NotNull UUID newCaptainId, @Size(max = 500) String reason) {
}
