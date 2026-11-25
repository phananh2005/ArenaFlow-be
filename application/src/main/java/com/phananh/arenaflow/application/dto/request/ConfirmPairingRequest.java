package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** Xác nhận gói ở {@code revision} FE đang hiển thị cho phía {@code teamId}. */
public record ConfirmPairingRequest(
        @NotNull UUID teamId,
        @NotNull @PositiveOrZero Integer revision) {
}
