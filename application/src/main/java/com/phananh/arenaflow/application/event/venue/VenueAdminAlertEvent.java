package com.phananh.arenaflow.application.event.venue;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cảnh báo Admin về một địa điểm; phát trong transaction ghi, listener xử lý sau khi commit.
 *
 * @param venueId     id địa điểm
 * @param venueName   tên địa điểm
 * @param alertType   loại cảnh báo: lâu không được chọn hoặc bị report đóng cửa
 * @param triggeredAt thời điểm phát cảnh báo, giờ Việt Nam không kèm múi giờ
 */
public record VenueAdminAlertEvent(UUID venueId, String venueName, VenueAlertType alertType, LocalDateTime triggeredAt) {
}
