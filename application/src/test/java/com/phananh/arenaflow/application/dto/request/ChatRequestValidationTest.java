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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRequestValidationTest {

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

    private static <T> Set<String> invalidFields(Set<ConstraintViolation<T>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    @Test
    void contentIsTrimmedThenCountedUpToOneThousand() {
        assertThat(new SendConversationMessageRequest("  hi  ").content()).isEqualTo("hi");
        assertThat(validator.validate(new SendConversationMessageRequest(" " + "x".repeat(1000) + " "))).isEmpty();
        assertThat(invalidFields(validator.validate(new SendConversationMessageRequest("x".repeat(1001)))))
                .contains("content");
        assertThat(invalidFields(validator.validate(new SendConversationMessageRequest("   ")))).contains("content");
        assertThat(invalidFields(validator.validate(new SendConversationMessageRequest(null)))).contains("content");
    }

    @Test
    void historyLimitDefaultsAndCursorsAreExclusive() {
        assertThat(new ChatHistoryQuery(null, null, null).limit()).isEqualTo(50);
        assertThat(validator.validate(new ChatHistoryQuery(UUID.randomUUID(), null, 100))).isEmpty();
        assertThat(invalidFields(validator.validate(new ChatHistoryQuery(UUID.randomUUID(), UUID.randomUUID(), 10))))
                .containsExactly("after");
        assertThat(invalidFields(validator.validate(new ChatHistoryQuery(null, null, 0)))).contains("limit");
        assertThat(invalidFields(validator.validate(new ChatHistoryQuery(null, null, 101)))).contains("limit");
    }
}
