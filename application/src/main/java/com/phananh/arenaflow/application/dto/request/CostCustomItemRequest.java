package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CostCustomItemRequest(
        @NotBlank @Size(max = 100) String itemName,
        @NotNull @Min(1) @Max(1_000_000_000L) Long amount) {
}
