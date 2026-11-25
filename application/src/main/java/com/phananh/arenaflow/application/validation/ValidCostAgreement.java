package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Quan hệ {@code costType} ↔ số tiền ↔ {@code costSplit} của một thoả thuận chi phí ({@link CostAgreementFields}). */
@Documented
@Constraint(validatedBy = ValidCostAgreementValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCostAgreement {
    String message() default "Thoả thuận chi phí không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
