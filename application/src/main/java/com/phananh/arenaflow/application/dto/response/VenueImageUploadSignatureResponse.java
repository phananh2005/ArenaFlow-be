package com.phananh.arenaflow.application.dto.response;

import java.time.OffsetDateTime;

public record VenueImageUploadSignatureResponse(String uploadUrl, String cloudName, String apiKey, String publicId,
                                                long timestamp, String allowedFormats, boolean overwrite,
                                                String signature, OffsetDateTime expiresAt) {
}
