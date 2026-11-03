package com.phananh.ArenaFlow.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CustomValidatorsTest {

    private static jakarta.validation.ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    record GenderHolder(@OneOf({"MALE", "FEMALE", "OTHER"}) String gender) {
    }

    record BirthHolder(@MinAge(13) LocalDate dateOfBirth) {
    }

    record SkillsHolder(@ValidSkillLevels List<String> skillLevels) {
    }

    @Test
    void oneOfAcceptsNullAndListedValuesOnly() {
        assertThat(validator.validate(new GenderHolder(null))).isEmpty();
        assertThat(validator.validate(new GenderHolder("FEMALE"))).isEmpty();
        assertThat(validator.validate(new GenderHolder("female"))).hasSize(1);
        assertThat(validator.validate(new GenderHolder("UNKNOWN"))).hasSize(1);
    }

    @Test
    void minAgeAcceptsNullAndExactlyThirteenYearsOld() {
        LocalDate today = LocalDate.now();
        assertThat(validator.validate(new BirthHolder(null))).isEmpty();
        assertThat(validator.validate(new BirthHolder(today.minusYears(13)))).isEmpty();
        assertThat(validator.validate(new BirthHolder(today.minusYears(30)))).isEmpty();

        Set<ConstraintViolation<BirthHolder>> tooYoung = validator.validate(new BirthHolder(today.minusYears(13).plusDays(1)));
        assertThat(tooYoung).hasSize(1);
    }

    @Test
    void validSkillLevelsAllowsUpToThreeDistinctKnownLabels() {
        assertThat(validator.validate(new SkillsHolder(null))).isEmpty();
        assertThat(validator.validate(new SkillsHolder(List.of()))).isEmpty();
        assertThat(validator.validate(new SkillsHolder(List.of("Chill", "Thi đấu", "Lão làng")))).isEmpty();

        assertThat(validator.validate(new SkillsHolder(List.of("Chill", "Thi đấu", "Lão làng", "Vui chơi")))).hasSize(1);
        assertThat(validator.validate(new SkillsHolder(List.of("Chill", "Chill")))).hasSize(1);
        assertThat(validator.validate(new SkillsHolder(List.of("Pro")))).hasSize(1);
        assertThat(validator.validate(new SkillsHolder(Arrays.asList("Chill", null)))).hasSize(1);
    }
}
