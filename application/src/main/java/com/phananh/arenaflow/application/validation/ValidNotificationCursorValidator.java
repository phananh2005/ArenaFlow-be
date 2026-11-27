package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.NotificationListQuery;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidNotificationCursorValidator implements ConstraintValidator<ValidNotificationCursor, NotificationListQuery> {

    @Override
    public boolean isValid(NotificationListQuery query, ConstraintValidatorContext context) {
        if (query == null || query.before() == null || query.after() == null) {
            return true;
        }
        // Gắn lỗi vào field: handler lỗi validation chỉ đọc lỗi theo field.
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("after")
                .addConstraintViolation();
        return false;
    }
}
