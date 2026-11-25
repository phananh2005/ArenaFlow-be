package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Quan hệ loại chi phí ↔ số tiền ↔ cách chia: không có loại → cả ba trống; có loại → bắt buộc cách chia, tiền sân có
 * đúng khi loại có tiền sân, tiền nước có đúng khi loại có tiền nước; "đội thua trả tiền nước" chỉ khi có tiền nước.
 */
@Documented
@Constraint(validatedBy = ValidCostAgreementValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCostAgreement {
    String message() default "Giá trị không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
