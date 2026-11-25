package com.phananh.arenaflow.application.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** "Ai giữ tiền cọc" chỉ hợp lệ khi có số tiền cọc; lỗi gắn vào {@code depositHolder}. */
@Documented
@Constraint(validatedBy = ValidDepositValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidDeposit {
    String message() default "Chỉ nhập người giữ tiền cọc khi có số tiền cọc";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
