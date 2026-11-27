package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class UniquePositionsValidator implements ConstraintValidator<UniquePositions, List<RecruitmentPositionRequest>> {

    @Override
    public boolean isValid(List<RecruitmentPositionRequest> positions, ConstraintValidatorContext context) {
        if (positions == null) {
            return true;
        }
        Set<String> seen = new HashSet<>();
        return positions.stream().filter(Objects::nonNull).map(RecruitmentPositionRequest::position)
                .filter(Objects::nonNull).allMatch(seen::add);
    }
}
