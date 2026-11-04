package com.phananh.ArenaFlow.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MarkInactiveRequest(@NotBlank @Size(max = 500) String reason) {
}
