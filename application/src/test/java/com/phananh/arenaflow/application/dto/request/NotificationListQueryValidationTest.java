package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationListQueryValidationTest {

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

    @Test
    void appliesDefaults() {
        NotificationListQuery query = new NotificationListQuery(null, null, null, null);

        assertThat(query.unreadOnly()).isFalse();
        assertThat(query.limit()).isEqualTo(50);
        assertThat(validator.validate(query)).isEmpty();
    }

    @Test
    void rejectsBeforeAndAfterTogetherOnAfterField() {
        Set<ConstraintViolation<NotificationListQuery>> violations =
                validator.validate(new NotificationListQuery(false, UUID.randomUUID(), UUID.randomUUID(), 20));

        assertThat(violations).extracting(violation -> violation.getPropertyPath().toString()).containsExactly("after");
    }

    @Test
    void acceptsOneCursor() {
        assertThat(validator.validate(new NotificationListQuery(true, UUID.randomUUID(), null, 1))).isEmpty();
        assertThat(validator.validate(new NotificationListQuery(true, null, UUID.randomUUID(), 100))).isEmpty();
    }

    @Test
    void rejectsLimitOutOfRange() {
        assertThat(validator.validate(new NotificationListQuery(null, null, null, 0)))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("limit");
        assertThat(validator.validate(new NotificationListQuery(null, null, null, 101)))
                .extracting(violation -> violation.getPropertyPath().toString()).containsExactly("limit");
    }
}
