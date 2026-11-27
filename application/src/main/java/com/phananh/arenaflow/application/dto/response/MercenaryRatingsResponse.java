package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Đánh giá của đơn theo góc nhìn người gọi; {@code received} luôn null khi người gọi chưa đánh giá. */
public record MercenaryRatingsResponse(UUID applicationId, String viewerSide, MercenaryRatingResponse given,
                                       MercenaryRatingResponse received) {
}
