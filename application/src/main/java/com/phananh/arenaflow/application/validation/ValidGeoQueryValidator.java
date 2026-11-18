package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.SearchVenueRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidGeoQueryValidator implements ConstraintValidator<ValidGeoQuery, SearchVenueRequest> {

    @Override
    public boolean isValid(SearchVenueRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }
        boolean hasLatitude = request.lat() != null;
        boolean hasLongitude = request.lng() != null;
        if (hasLatitude != hasLongitude) {
            addFieldViolation(context, "lat");
            return false;
        }
        if (!hasLatitude && request.radiusKm() != null) {
            addFieldViolation(context, "radiusKm");
            return false;
        }
        return true;
    }

    private static void addFieldViolation(ConstraintValidatorContext context, String field) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(field)
                .addConstraintViolation();
    }
}
