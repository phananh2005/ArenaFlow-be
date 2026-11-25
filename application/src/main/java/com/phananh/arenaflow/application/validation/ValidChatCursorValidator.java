package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.ChatHistoryQuery;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidChatCursorValidator implements ConstraintValidator<ValidChatCursor, ChatHistoryQuery> {

    @Override
    public boolean isValid(ChatHistoryQuery query, ConstraintValidatorContext context) {
        if (query == null || query.before() == null || query.after() == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("after")
                .addConstraintViolation();
        return false;
    }
}
