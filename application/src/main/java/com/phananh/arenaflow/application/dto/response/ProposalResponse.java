package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Phương án đã chốt: một lựa chọn địa điểm (thông tin mới nhất) + một khung giờ của kèo. */
public record ProposalResponse(UUID venueId, String venueName, String address, String venueStatus, UUID timeOptionId,
                               LocalDateTime startsAt, LocalDateTime endsAt) {
}
