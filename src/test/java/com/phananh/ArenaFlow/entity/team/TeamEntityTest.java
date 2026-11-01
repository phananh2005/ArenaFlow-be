package com.phananh.ArenaFlow.entity.team;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamEntityTest {

    @Test
    void whenCreateTeam_thenIdFieldTypeIsUUID() {
        Team team = new Team();
        team.setName("Test Team");

        // This test verifies that the id field is of type UUID
        // Will fail at compile time if id is not UUID type
        UUID id = team.getId();

        // Verify the field accepts UUID assignment
        UUID testId = UUID.randomUUID();
        team.setId(testId);

        assertThat(team.getId()).isEqualTo(testId);
    }

    @Test
    void whenSetCreatedBy_thenAcceptsUUID() {
        Team team = new Team();
        UUID creatorId = UUID.randomUUID();

        team.setCreatedBy(creatorId);

        assertThat(team.getCreatedBy()).isEqualTo(creatorId);
    }
}
