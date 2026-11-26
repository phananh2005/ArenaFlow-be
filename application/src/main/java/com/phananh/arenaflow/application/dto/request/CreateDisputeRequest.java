package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Bằng chứng tranh chấp (văn bản); được trim trước Bean Validation. */
public record CreateDisputeRequest(@NotBlank @Size(max = 1000) String evidence) {

    public CreateDisputeRequest {
        evidence = evidence == null ? null : evidence.trim();
    }
}
