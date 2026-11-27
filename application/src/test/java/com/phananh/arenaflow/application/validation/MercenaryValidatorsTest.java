package com.phananh.arenaflow.application.validation;

import com.phananh.arenaflow.application.dto.request.RecruitmentPositionRequest;
import com.phananh.arenaflow.common.time.AppTime;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MercenaryValidatorsTest {

    private static ValidatorFactory factory;
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

    record PositionsHolder(@UniquePositions List<RecruitmentPositionRequest> positions) {
    }

    @ValidExternalMatchSchedule
    record ScheduleHolder(LocalDateTime startsAt, LocalDateTime endsAt) implements ExternalMatchScheduleInput {
    }

    @ValidRating
    record RatingHolder(Boolean isNoShow, Integer score) implements RatingScoreInput {
    }

    @Test
    void uniquePositionsRejectsDuplicatePositionsOnly() {
        assertThat(validator.validate(new PositionsHolder(null))).isEmpty();
        assertThat(validator.validate(new PositionsHolder(List.of(
                new RecruitmentPositionRequest("Tiền vệ", 1), new RecruitmentPositionRequest("Hậu vệ", 1))))).isEmpty();
        assertThat(validator.validate(new PositionsHolder(List.of(
                new RecruitmentPositionRequest("Tiền vệ", 1), new RecruitmentPositionRequest(" Tiền vệ ", 2)))))
                .hasSize(1);
    }

    @Test
    void scheduleRequiresLeadTimeOrderAndMaxDuration() {
        LocalDateTime now = AppTime.now();
        LocalDateTime start = now.plusHours(2);

        assertThat(fields(new ScheduleHolder(start, start.plusHours(12)))).isEmpty();
        assertThat(fields(new ScheduleHolder(now.plusMinutes(30), now.plusHours(2)))).containsExactly("startsAt");
        assertThat(fields(new ScheduleHolder(start, start))).containsExactly("endsAt");
        assertThat(fields(new ScheduleHolder(start, start.plusHours(12).plusMinutes(1)))).containsExactly("endsAt");
        assertThat(fields(new ScheduleHolder(null, null))).isEmpty();
    }

    @Test
    void ratingRequiresScoreUnlessNoShow() {
        assertThat(validator.validate(new RatingHolder(null, 5))).isEmpty();
        assertThat(validator.validate(new RatingHolder(true, null))).isEmpty();
        assertThat(validator.validate(new RatingHolder(false, null))).hasSize(1);
        assertThat(validator.validate(new RatingHolder(false, 6))).hasSize(1);
        assertThat(validator.validate(new RatingHolder(true, 3))).hasSize(1);
    }

    private static Set<String> fields(ScheduleHolder holder) {
        return validator.validate(holder).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }
}
