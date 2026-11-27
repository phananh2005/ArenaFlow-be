package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.support.match.MatchRules;
import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;

public class ValidExternalMatchScheduleValidator
        implements ConstraintValidator<ValidExternalMatchSchedule, ExternalMatchScheduleInput> {

    @Override
    public boolean isValid(ExternalMatchScheduleInput value, ConstraintValidatorContext context) {
        if (value == null || value.startsAt() == null || value.endsAt() == null) {
            return true;
        }
        LocalDateTime start = value.startsAt();
        LocalDateTime end = value.endsAt();
        if (start.isBefore(AppTime.now().plus(MatchRules.MIN_LEAD_TIME))) {
            return violation(context, "startsAt");
        }
        if (!end.isAfter(start) || end.isAfter(start.plus(MatchRules.MAX_DURATION))) {
            return violation(context, "endsAt");
        }
        return true;
    }

    private static boolean violation(ConstraintValidatorContext context, String field) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }
}
