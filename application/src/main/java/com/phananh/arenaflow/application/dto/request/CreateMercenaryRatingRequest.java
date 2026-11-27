package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.RatingScoreInput;
import com.phananh.arenaflow.application.validation.ValidRating;
import jakarta.validation.constraints.Size;

/** Đánh giá theo đơn: "Không đến" (chỉ phía đội trưởng — kiểm ở service) hoặc 1..5 sao. */
@ValidRating
public record CreateMercenaryRatingRequest(Boolean isNoShow, Integer score, @Size(max = 500) String comment)
        implements RatingScoreInput {

    public CreateMercenaryRatingRequest {
        isNoShow = isNoShow != null && isNoShow;
        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }
}
