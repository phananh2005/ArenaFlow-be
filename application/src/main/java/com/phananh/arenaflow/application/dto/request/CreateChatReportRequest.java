package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Report "giao tiếp tệ" qua chat; {@code reason} được trim trước Bean Validation. */
public record CreateChatReportRequest(@NotBlank @Size(max = 1000) String reason) {

    public CreateChatReportRequest {
        reason = reason == null ? null : reason.trim();
    }
}
