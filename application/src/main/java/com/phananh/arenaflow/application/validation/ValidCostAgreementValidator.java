package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.UpdateCostAgreementRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class ValidCostAgreementValidator implements ConstraintValidator<ValidCostAgreement, UpdateCostAgreementRequest> {

    private static final Set<String> WITH_VENUE = Set.of("VENUE_ONLY", "BOTH");
    private static final Set<String> WITH_DRINKS = Set.of("DRINKS_ONLY", "BOTH");
    private static final String LOSER_PAYS_DRINKS = "LOSER_PAYS_DRINKS";

    @Override
    public boolean isValid(UpdateCostAgreementRequest request, ConstraintValidatorContext context) {
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

        addIfInvalid(context, splitValid, "costSplit");
        addIfInvalid(context, venueValid, "venueAmount");
        addIfInvalid(context, drinksValid, "drinksAmount");
        return splitValid && venueValid && drinksValid;
    }

    private static void addIfInvalid(ConstraintValidatorContext context, boolean valid, String field) {
        if (!valid) {
            context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                    .addPropertyNode(field)
                    .addConstraintViolation();
        }
    }
}
