package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import com.phananh.arenaflow.application.dto.response.MatchRatingsResponse;
import com.phananh.arenaflow.application.dto.response.RatingCreatedResponse;

import java.util.UUID;

public interface RatingService {

    /**
     * Đội của người gọi (phía người gọi là đội trưởng ACTIVE) đánh giá đội kia. Thứ tự lỗi: MATCH_001 → TEAM_002 →
     * TEAM_015 → RATING_003 → RATING_001 → RATING_002. Đánh giá đầu tiên của trận ARCHIVED chuyển trận COMPLETED và
     * cộng điểm hoàn thành trong cùng transaction; thử lại xung đột → MATCH_014. 3–5 truy vấn kể cả refresh huy hiệu.
     */
    RatingCreatedResponse createRating(UUID matchId, UUID callerId, CreateRatingRequest request);

    /**
     * Đánh giá đội {@code teamId} đã gửi / đã nhận. Thứ tự lỗi: MATCH_001 → TEAM_001 → TEAM_016 → TEAM_015 → TEAM_002
     * → MATCH_009. {@code received} chỉ hiện sau khi đội đã đánh giá. 2 truy vấn.
     */
    MatchRatingsResponse getMatchRatings(UUID matchId, UUID teamId, UUID callerId);
}
