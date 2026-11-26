package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Quyết định của Admin; {@code note} được trim trước Bean Validation. */
public record ResolveDisputeRequest(
        @NotNull @OneOf({"CONFIRMED", "REJECTED"}) String decision,
        @NotBlank @Size(max = 500) String note) {

    public ResolveDisputeRequest {
        note = note == null ? null : note.trim();
    }
}
