package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.UpdatePairingCostAgreementRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidDepositValidator implements ConstraintValidator<ValidDeposit, UpdatePairingCostAgreementRequest> {

    @Override
    public boolean isValid(UpdatePairingCostAgreementRequest request, ConstraintValidatorContext context) {
        // Chuỗi rỗng / chỉ khoảng trắng = không nhập (service lưu null).
        if (request == null || request.depositHolder() == null || request.depositHolder().isBlank()
                || request.depositAmount() != null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode("depositHolder")
                .addConstraintViolation();
        return false;
    }
}
