package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Quy tắc field theo luồng và thời gian của kèo; lỗi gắn vào tên field của request ({@code startsAt}, {@code endsAt},
 * {@code expiresAt}…).
 */
@Documented
@Constraint(validatedBy = ValidMatchScheduleValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMatchSchedule {
    String message() default "Giá trị không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
