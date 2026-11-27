package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Giờ trận ngoài hệ thống: bắt đầu ≥ hiện tại + thời gian tối thiểu (lỗi ở {@code startsAt}); kết thúc sau bắt đầu và
 * thời lượng không quá tối đa (lỗi ở {@code endsAt}). Thiếu giờ → bỏ qua (để {@code @NotNull} báo).
 */
@Documented
@Constraint(validatedBy = ValidExternalMatchScheduleValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidExternalMatchSchedule {
    String message() default "Giờ trận không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
