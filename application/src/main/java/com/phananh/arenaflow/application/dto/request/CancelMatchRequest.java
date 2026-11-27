package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** {@code expectedStatus}: trạng thái kèo client đang hiển thị — khác trạng thái hiện tại → MATCH_015. */
public record CancelMatchRequest(
        @NotNull UUID teamId,
        @NotNull @OneOf({"OPEN", "CONFIRMED"}) String expectedStatus,
        @Size(max = 500) String reason) {
}
