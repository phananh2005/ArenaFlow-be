package com.phananh.arenaflow.domain.entity.user;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityTest {

    @Test
    void whenCreateUser_thenIdFieldTypeIsUUID() {
        User user = new User();
        user.setDisplayName("Test User");

        // This test verifies that the id field is of type UUID
        // Will fail at compile time if id is not UUID type
        UUID id = user.getId();

        // Verify the field accepts UUID assignment
        UUID testId = UUID.randomUUID();
        user.setId(testId);

        assertThat(user.getId()).isEqualTo(testId);
    }

    @Test
    void whenSetWardId_thenAcceptsUUID() {
        User user = new User();
        UUID wardId = UUID.randomUUID();

        user.setWardId(wardId);

        assertThat(user.getWardId()).isEqualTo(wardId);
    }
}
