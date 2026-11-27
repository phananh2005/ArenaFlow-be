package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uy tín cá nhân. */
public record UserReputationResponse(UUID userId, int reliabilityScore, String reliabilityLevel, Completion completion,
                                     Rating rating, Verification verification) {

    /** Tỉ lệ hoàn thành; {@code completionRate} null khi chưa đủ số trận được tính. */
    public record Completion(int completedMatches, int countedMatches, Integer completionRate, boolean isNewcomer) {
    }

    /** Sao trung bình (1 chữ số thập phân, null khi chưa có sao). */
    public record Rating(BigDecimal averageRating, int ratingCount) {
    }

    public record Verification(boolean isVerified, LocalDateTime verifiedAt) {
    }
}
