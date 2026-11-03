package com.phananh.ArenaFlow.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Tối đa 3 nhãn trình độ, không trùng, thuộc {@code SkillLevel.labels()} (BR-T02); null = hợp lệ. */
@Documented
@Constraint(validatedBy = ValidSkillLevelsValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidSkillLevels {
    String message() default "Trình độ tối đa 3 nhãn, không trùng, thuộc danh sách cho phép";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
