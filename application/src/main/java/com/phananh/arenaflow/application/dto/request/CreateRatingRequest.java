package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.ValidRating;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** {@code isNoShow} vắng → false; {@code comment} được trim, rỗng → null trước Bean Validation. */
@ValidRating
public record CreateRatingRequest(
        Boolean isNoShow,
        Integer score,
        @Size(max = 500) String comment,
        @Min(0) @Max(999) Integer raterTeamScore,
        @Min(0) @Max(999) Integer rateeTeamScore) {

    public CreateRatingRequest {
        isNoShow = isNoShow != null && isNoShow;
        comment = comment == null || comment.isBlank() ? null : comment.trim();
    }
}
