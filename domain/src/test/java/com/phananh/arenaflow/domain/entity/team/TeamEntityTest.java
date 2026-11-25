package com.phananh.arenaflow.domain.entity.team;

import com.phananh.arenaflow.domain.entity.ColumnMapping;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Entity đội: kiểu id, người tạo, khoá cột uy tín, trạng thái giải tán và metadata giải tán. */
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
    void reputationColumnsAreNeverWrittenByDirtyChecking() {
        for (String field : List.of("reliabilityScore", "cleanStreak", "reportCountNoshow", "reportCountBadPlay",
                "reportCountBadCommunication", "reportCountSkillMismatch")) {
            assertThat(ColumnMapping.column(Team.class, field).updatable()).as(field).isFalse();
        }
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
        LocalDateTime disbandedAt = LocalDateTime.of(2026, 11, 20, 19, 0);

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
