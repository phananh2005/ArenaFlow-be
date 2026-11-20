package com.phananh.arenaflow.application.event.venue;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Phát trong transaction ghi; listener xử lý sau khi commit. */
public record VenueAdminAlertEvent(UUID venueId, String venueName, VenueAlertType alertType, OffsetDateTime triggeredAt) {
}
