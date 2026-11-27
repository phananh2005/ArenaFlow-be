package com.phananh.arenaflow.application.validation;

/** Request đánh giá có lựa chọn "Không đến" và số sao — đích của {@link ValidRating}. */
public interface RatingScoreInput {

    Boolean isNoShow();

    Integer score();
}
