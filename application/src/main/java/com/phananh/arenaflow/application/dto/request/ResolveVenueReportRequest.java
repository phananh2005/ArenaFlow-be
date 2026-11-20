package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotBlank;

public record ResolveVenueReportRequest(
        @NotBlank @OneOf({"VERIFIED", "REJECTED"}) String status) {
}
