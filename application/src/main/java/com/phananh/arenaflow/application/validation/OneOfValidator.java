package com.phananh.arenaflow.application.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class OneOfValidator implements ConstraintValidator<OneOf, String> {

    private Set<String> allowed;

    @Override
    public void initialize(OneOf annotation) {
        allowed = Set.of(annotation.value());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || allowed.contains(value);
    }
}
