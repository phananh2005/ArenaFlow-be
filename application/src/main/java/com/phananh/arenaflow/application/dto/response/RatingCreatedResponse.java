package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đánh giá vừa gửi + trạng thái trận sau khi gửi (luôn COMPLETED). */
public record RatingCreatedResponse(UUID ratingId, UUID matchId, UUID raterTeamId, UUID rateeTeamId, boolean isNoShow,
                                    Integer score, String comment, Integer raterTeamScore, Integer rateeTeamScore,
                                    LocalDateTime createdAt, String matchStatus) {
}
