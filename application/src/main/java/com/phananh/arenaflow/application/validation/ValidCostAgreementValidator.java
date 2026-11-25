package com.phananh.arenaflow.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Objects;
import java.util.Set;

public class ValidCostAgreementValidator implements ConstraintValidator<ValidCostAgreement, CostAgreementFields> {

    private static final Set<String> WITH_VENUE = Set.of("VENUE_ONLY", "BOTH");
    private static final Set<String> WITH_DRINKS = Set.of("DRINKS_ONLY", "BOTH");
    private static final String LOSER_PAYS_DRINKS = "LOSER_PAYS_DRINKS";

    @Override
    public boolean isValid(CostAgreementFields request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        context.disableDefaultConstraintViolation();
        String costType = request.costType();
        boolean hasVenue = costType != null && WITH_VENUE.contains(costType);
        boolean hasDrinks = costType != null && WITH_DRINKS.contains(costType);

        boolean splitValid = costType == null ? request.costSplit() == null
                : request.costSplit() != null && (!LOSER_PAYS_DRINKS.equals(request.costSplit()) || hasDrinks);
        boolean venueValid = hasVenue == (request.venueAmount() != null);
        boolean drinksValid = hasDrinks == (request.drinksAmount() != null);

        // Phần tử null trong danh sách khoản tự nhập (lỗi phía client): báo trên cả danh sách,
        // không để service gặp null rồi trả lỗi hệ thống.
        boolean itemsValid = request.customItems() == null || request.customItems().stream().noneMatch(Objects::isNull);

        addIfInvalid(context, splitValid, "costSplit");
        addIfInvalid(context, venueValid, "venueAmount");
        addIfInvalid(context, drinksValid, "drinksAmount");
        addIfInvalid(context, itemsValid, "customItems");
        return splitValid && venueValid && drinksValid && itemsValid;
    }

    private static void addIfInvalid(ConstraintValidatorContext context, boolean valid, String field) {
        if (!valid) {
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(field)
                    .addConstraintViolation();
        }
    }
}
