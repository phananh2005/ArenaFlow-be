package com.phananh.arenaflow.domain.entity.user;

import com.phananh.arenaflow.domain.entity.ColumnMapping;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Entity người dùng: kiểu id, phường/xã và khoá các cột điểm/chuỗi trận sạch/bộ đếm report trước dirty checking. */
class UserEntityTest {

    @Test
    void whenCreateUser_thenIdFieldTypeIsUUID() {
        User user = User.builder()
                .displayName("Test User")
                .build();

        // This test verifies that the id field is of type UUID
        // Will fail at compile time if id is not UUID type
        UUID id = user.getId();

        // Verify the field accepts UUID assignment
        UUID testId = UUID.randomUUID();
        user.setId(testId);

        assertThat(user.getId()).isEqualTo(testId);
    }

    @Test
    void reputationColumnsAreNeverWrittenByDirtyChecking() {
        for (String field : List.of("reliabilityScore", "cleanStreak", "reportCountNoshow", "reportCountBadPlay",
                "reportCountBadCommunication", "reportCountSkillMismatch")) {
            assertThat(ColumnMapping.column(User.class, field).updatable()).as(field).isFalse();
        }
    }

    @Test
    void derivedMatchCountersAndAverageRatingAreRemoved() {
        for (String field : List.of("totalMatchesCompleted", "totalMatchesConfirmed", "averageRating")) {
            assertThat(ColumnMapping.hasField(User.class, field)).as(field).isFalse();
        }
    }

    @Test
    void whenSetWardId_thenAcceptsUUID() {
        User user = User.builder().build();
        UUID wardId = UUID.randomUUID();

        user.setWardId(wardId);

        assertThat(user.getWardId()).isEqualTo(wardId);
    }
}
