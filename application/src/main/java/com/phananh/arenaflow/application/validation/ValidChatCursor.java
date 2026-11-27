package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Không gửi cùng lúc {@code before} và {@code after}; lỗi gắn {@code after}. */
@Documented
@Constraint(validatedBy = ValidChatCursorValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidChatCursor {
    String message() default "Chỉ gửi một trong before hoặc after";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
