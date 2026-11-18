package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Yêu cầu lat/lng cùng xuất hiện; radiusKm chỉ hợp lệ khi có toạ độ. */
@Documented
@Constraint(validatedBy = ValidGeoQueryValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidGeoQuery {
    String message() default "Toạ độ tìm kiếm không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
