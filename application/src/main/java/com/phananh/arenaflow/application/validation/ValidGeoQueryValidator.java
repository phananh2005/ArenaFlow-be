package com.phananh.arenaflow.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Áp cho mọi request tìm theo toạ độ (địa điểm, kèo…) qua {@link GeoQueryRequest}. */
public class ValidGeoQueryValidator implements ConstraintValidator<ValidGeoQuery, GeoQueryRequest> {

    @Override
    public boolean isValid(GeoQueryRequest request, ConstraintValidatorContext context) {
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
