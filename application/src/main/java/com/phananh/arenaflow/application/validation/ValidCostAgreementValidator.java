package com.phananh.arenaflow.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Bản tạm: chưa kiểm quy tắc (quy tắc thuộc thoả thuận chi phí của kèo luồng A). */
public class ValidCostAgreementValidator implements ConstraintValidator<ValidCostAgreement, CostAgreementFields> {

    @Override
    public boolean isValid(CostAgreementFields value, ConstraintValidatorContext context) {
        return true;
    }
}
