package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Danh sách vị trí tuyển không trùng {@code position}; null = hợp lệ. */
@Documented
@Constraint(validatedBy = UniquePositionsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface UniquePositions {
    String message() default "Vị trí không được trùng";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
