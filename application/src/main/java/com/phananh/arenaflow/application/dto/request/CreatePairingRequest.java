package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Nhận kèo luồng B: {@code teamId} = đội nhận kèo. */
public record CreatePairingRequest(@NotNull UUID teamId) {
}
