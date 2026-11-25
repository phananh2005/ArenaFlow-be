package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Khung giờ của kèo luồng B (kèo luồng A: danh sách rỗng). */
public record MatchTimeOptionResponse(UUID timeOptionId, LocalDateTime startsAt, LocalDateTime endsAt) {
}
