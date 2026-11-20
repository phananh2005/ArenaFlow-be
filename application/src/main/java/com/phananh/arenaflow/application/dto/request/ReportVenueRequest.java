package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportVenueRequest(
        @NotBlank @OneOf({"CLOSED", "WRONG_INFO", "OTHER"}) String reason,
        @Size(max = 1000) String detail) {
}
