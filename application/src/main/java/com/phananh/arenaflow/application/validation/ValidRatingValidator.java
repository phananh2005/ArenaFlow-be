package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.CreateRatingRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.ArrayList;
import java.util.List;

public class ValidRatingValidator implements ConstraintValidator<ValidRating, CreateRatingRequest> {

    private static final int MIN_SCORE = 1;
    private static final int MAX_SCORE = 5;

    @Override
    public boolean isValid(CreateRatingRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        List<String> invalid = new ArrayList<>();
        boolean noShow = Boolean.TRUE.equals(request.isNoShow());
        Integer score = request.score();
        if (noShow ? score != null : score == null || score < MIN_SCORE || score > MAX_SCORE) {
            invalid.add("score");
        }
        boolean hasRaterScore = request.raterTeamScore() != null;
        boolean hasRateeScore = request.rateeTeamScore() != null;
        if (noShow) {
            // "Không đến" không có tỷ số: báo mọi tỷ số đã gửi.
            if (hasRaterScore) {
                invalid.add("raterTeamScore");
            }
            if (hasRateeScore) {
                invalid.add("rateeTeamScore");
            }
        } else if (hasRaterScore != hasRateeScore) {
            // Chỉ gửi một tỷ số: báo field còn thiếu.
            invalid.add(hasRaterScore ? "rateeTeamScore" : "raterTeamScore");
        }
        if (invalid.isEmpty()) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        invalid.forEach(field -> context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(field)
                .addConstraintViolation());
        return false;
    }
}
