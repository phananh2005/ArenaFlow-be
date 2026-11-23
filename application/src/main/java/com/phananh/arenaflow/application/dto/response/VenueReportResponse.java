package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Report địa điểm vừa gửi.
 *
 * @param reportId  id report
 * @param venueId   id địa điểm bị report
 * @param reason    lý do ({@code CLOSED}, {@code WRONG_INFO}, {@code OTHER})
 * @param detail    mô tả thêm; có thể null
 * @param status    luôn {@code PENDING} khi vừa tạo
 * @param createdAt thời điểm gửi
 */
public record VenueReportResponse(UUID reportId, UUID venueId, String reason, String detail, String status,
                                  LocalDateTime createdAt) {
}
