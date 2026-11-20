package com.phananh.arenaflow.application.support.storage;

import java.time.OffsetDateTime;

public record UploadSignature(String uploadUrl, String cloudName, String apiKey, String publicId, long timestamp,
                              String allowedFormats, boolean overwrite, String signature, OffsetDateTime expiresAt) {
}
