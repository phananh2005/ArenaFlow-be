package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotNull @OneOf({"NOSHOW", "BAD_PLAY", "BAD_COMMUNICATION", "SKILL_MISMATCH"}) String reportType,
        @NotBlank @Size(max = 1000) String reason) {

    public CreateReportRequest {
        reason = reason == null ? null : reason.trim();
    }
}
