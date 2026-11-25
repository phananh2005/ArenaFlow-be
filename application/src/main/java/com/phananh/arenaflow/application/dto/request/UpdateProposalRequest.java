package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

/** Chốt phương án: một lựa chọn địa điểm + một khung giờ của kèo, ở {@code revision} FE đang hiển thị. */
public record UpdateProposalRequest(
        @NotNull UUID teamId,
        @NotNull @PositiveOrZero Integer revision,
        @NotNull UUID venueId,
        @NotNull UUID timeOptionId) {
}
