package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Bộ lọc tìm kèo ngoài toạ độ (toạ độ kiểm ở {@link ValidGeoQuery}): {@code skillLevel} thuộc 9 nhãn trình độ,
 * {@code startsAtFrom ≤ startsAtTo} (lỗi gắn {@code startsAtTo}).
 */
@Documented
@Constraint(validatedBy = ValidMatchSearchValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidMatchSearch {
    String message() default "Giá trị không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
