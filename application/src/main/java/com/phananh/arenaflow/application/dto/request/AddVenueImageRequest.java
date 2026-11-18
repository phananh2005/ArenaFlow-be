package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddVenueImageRequest(
        @NotBlank @Size(max = 255) String publicId,
        @NotNull @Positive Long version,
        @NotBlank @OneOf({"jpg", "jpeg", "png", "webp"}) String format,
        @NotBlank @Size(max = 128) String signature,
        Boolean isPrimary) {
}
