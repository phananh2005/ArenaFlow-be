package com.phananh.arenaflow.domain.entity.team;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamEntityTest {

    @Test
    void whenCreateTeam_thenIdFieldTypeIsUUID() {
        Team team = Team.builder()
                .name("Test Team")
                .build();

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
        Team team = Team.builder().build();
        UUID creatorId = UUID.randomUUID();

        team.setCreatedBy(creatorId);

        assertThat(team.getCreatedBy()).isEqualTo(creatorId);
    }

    @Test
    void teamStatusIncludesDisbanded() {
        assertThat(Team.TeamStatus.valueOf("DISBANDED")).isEqualTo(Team.TeamStatus.DISBANDED);
        assertThat(Team.TeamStatus.values())
                .containsExactly(Team.TeamStatus.ACTIVE, Team.TeamStatus.INACTIVE, Team.TeamStatus.DISBANDED);
    }

    @Test
    void whenSetDisbandMetadata_thenGettersReturnValuesAndInactiveReasonIsKept() {
        Team team = Team.builder()
                .inactiveReason("Vi phạm quy định")
                .build();
        UUID captainId = UUID.randomUUID();
        OffsetDateTime disbandedAt = OffsetDateTime.now();

        team.setStatus(Team.TeamStatus.DISBANDED);
        team.setDisbandReason("Đội trưởng giải tán đội");
        team.setDisbandedAt(disbandedAt);
        team.setDisbandedBy(captainId);

        assertThat(team.getStatus()).isEqualTo(Team.TeamStatus.DISBANDED);
        assertThat(team.getDisbandReason()).isEqualTo("Đội trưởng giải tán đội");
        assertThat(team.getDisbandedAt()).isEqualTo(disbandedAt);
        assertThat(team.getDisbandedBy()).isEqualTo(captainId);
        assertThat(team.getInactiveReason()).isEqualTo("Vi phạm quy định");
    }
}
