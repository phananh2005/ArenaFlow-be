package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record LastMessagePreviewResponse(UUID messageId, String contentPreview, UUID senderPartyId, String senderName,
                                         LocalDateTime createdAt) {
}
