package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh giá sao: {@code score} 1..5 bắt buộc. "Đội kia không đến": không có {@code score} và tỷ số. Hai tỷ số cùng có
 * hoặc cùng vắng. Lỗi gắn theo tên field.
 */
@Documented
@Constraint(validatedBy = ValidRatingValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidRating {
    String message() default "Đánh giá không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
