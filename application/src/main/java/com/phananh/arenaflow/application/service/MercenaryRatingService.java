package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.request.CreateMercenaryRatingRequest;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingResponse;
import com.phananh.arenaflow.application.dto.response.MercenaryRatingsResponse;

import java.util.UUID;

/** Đánh giá sau trận giữa đội tuyển và mercenary theo đơn. */
public interface MercenaryRatingService {

    MercenaryRatingResponse createRating(UUID applicationId, UUID callerId, CreateMercenaryRatingRequest request);

    MercenaryRatingsResponse getRatings(UUID applicationId, UUID callerId);
}
