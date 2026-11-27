package com.phananh.arenaflow.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidRatingValidator implements ConstraintValidator<ValidRating, RatingScoreInput> {

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;

    @Override
    public boolean isValid(RatingScoreInput request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        boolean noShow = Boolean.TRUE.equals(request.isNoShow());
        Integer score = request.score();
        boolean valid = noShow ? score == null : score != null && score >= MIN_SCORE && score <= MAX_SCORE;
        if (!valid) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("score")
                    .addConstraintViolation();
        }
        return valid;
    }
}
