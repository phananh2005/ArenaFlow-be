package com.phananh.arenaflow.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uy tín đội: điểm + mức hiển thị, tỉ lệ hoàn thành, sao trung bình, huy hiệu xác minh. */
public record TeamReputationResponse(UUID teamId, int reliabilityScore, String reliabilityLevel,
                                     CompletionResponse completion, RatingSummaryResponse rating,
                                     VerificationResponse verification) {

    /** {@code completionRate} null khi số trận được tính dưới ngưỡng "mới tham gia". */
    public record CompletionResponse(int completedMatches, int countedMatches, Integer completionRate,
                                     boolean isNewcomer) {
    }

    /** {@code averageRating} 1 chữ số thập phân; null khi chưa có sao nào. */
    public record RatingSummaryResponse(BigDecimal averageRating, int ratingCount) {
    }

    public record VerificationResponse(boolean isVerified, LocalDateTime verifiedAt) {
    }
}
