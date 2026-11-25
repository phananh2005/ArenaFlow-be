package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.SearchMatchRequest;
import com.phananh.arenaflow.common.enums.SkillLevel;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidMatchSearchValidator implements ConstraintValidator<ValidMatchSearch, SearchMatchRequest> {

    @Override
    public boolean isValid(SearchMatchRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (request.skillLevel() != null && !SkillLevel.labels().contains(request.skillLevel())) {
            addFieldViolation(context, "skillLevel");
            valid = false;
        }
        if (request.startsAtFrom() != null && request.startsAtTo() != null
                && request.startsAtFrom().isAfter(request.startsAtTo())) {
            addFieldViolation(context, "startsAtTo");
            valid = false;
        }
        return valid;
    }

    private static void addFieldViolation(ConstraintValidatorContext context, String field) {
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
