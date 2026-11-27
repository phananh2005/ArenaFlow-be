package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Danh sách thông báo: không gửi cùng lúc {@code before} và {@code after}; lỗi gắn vào field {@code after}. */
@Documented
@Constraint(validatedBy = ValidNotificationCursorValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidNotificationCursor {
    String message() default "Chỉ gửi một trong hai tham số before hoặc after";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
