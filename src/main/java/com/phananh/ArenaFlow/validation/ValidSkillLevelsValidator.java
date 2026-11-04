package com.phananh.ArenaFlow.validation;

import com.phananh.ArenaFlow.common.SkillLevel;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;

public class ValidSkillLevelsValidator implements ConstraintValidator<ValidSkillLevels, List<String>> {

    private static final int MAX_LABELS = 3;

    @Override
    public boolean isValid(List<String> skillLevels, ConstraintValidatorContext context) {
        if (skillLevels == null) {
            return true;
        }
        return skillLevels.size() <= MAX_LABELS
                && skillLevels.stream().allMatch(label -> label != null && SkillLevel.labels().contains(label))
                && new HashSet<>(skillLevels).size() == skillLevels.size();
    }
}
