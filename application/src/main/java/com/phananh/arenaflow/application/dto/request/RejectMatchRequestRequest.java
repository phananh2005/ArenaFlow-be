package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.Size;

public record RejectMatchRequestRequest(@Size(max = 500) String reason) {
}
