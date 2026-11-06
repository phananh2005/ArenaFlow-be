package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Ngày sinh phải đủ {@code value} tuổi tính đến hôm nay; null = hợp lệ. */
@Documented
@Constraint(validatedBy = MinAgeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MinAge {
    int value();

    String message() default "Chưa đủ tuổi tối thiểu";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
