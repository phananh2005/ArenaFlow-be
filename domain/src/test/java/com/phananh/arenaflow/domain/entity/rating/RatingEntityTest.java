package com.phananh.arenaflow.domain.entity.rating;

import com.phananh.arenaflow.common.time.AppTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.phananh.arenaflow.domain.entity.ColumnMapping.column;
import static org.assertj.core.api.Assertions.assertThat;

class RatingEntityTest {

    @Test
    void builderDefaultsToNotNoShowAndKeepsIntegerScoreAndTeamScores() {
        UUID creator = UUID.randomUUID();
        Rating rating = Rating.builder()
                .matchId(UUID.randomUUID())
                .raterType(Rating.RaterType.TEAM).raterId(UUID.randomUUID())
                .rateeType(Rating.RateeType.TEAM).rateeId(UUID.randomUUID())
                .score(5).raterTeamScore(3).rateeTeamScore(2)
                .createdByUserId(creator)
                .build();

        assertThat(rating.isNoShow()).isFalse();
        assertThat(rating.getScore()).isEqualTo(5);
        assertThat(rating.getRaterTeamScore()).isEqualTo(3);
        assertThat(rating.getRateeTeamScore()).isEqualTo(2);
        assertThat(rating.getCreatedByUserId()).isEqualTo(creator);
    }

    @Test
    void noShowRatingHasNoScore() {
        Rating rating = Rating.builder().noShow(true).build();

        assertThat(rating.isNoShow()).isTrue();
        assertThat(rating.getScore()).isNull();
    }

    @Test
    void prePersistSetsVietnamLocalCreatedAt() {
        Rating rating = Rating.builder().build();
        LocalDateTime before = AppTime.now().minusSeconds(1);

        rating.onCreate();

        assertThat(rating.getCreatedAt()).isAfter(before);
    }

    @Test
    void everyColumnIsInsertOnly() {
        for (String field : List.of("matchId", "raterType", "raterId", "rateeType", "rateeId", "score", "noShow",
                "comment", "raterTeamScore", "rateeTeamScore", "createdByUserId", "createdAt")) {
            assertThat(column(Rating.class, field).updatable()).as(field).isFalse();
        }
        assertThat(column(Rating.class, "score").nullable()).isTrue();
        assertThat(column(Rating.class, "noShow").name()).isEqualTo("is_no_show");
        assertThat(column(Rating.class, "raterTeamScore").name()).isEqualTo("rater_team_score");
        assertThat(column(Rating.class, "rateeTeamScore").name()).isEqualTo("ratee_team_score");
        assertThat(column(Rating.class, "createdByUserId").nullable()).isFalse();
    }
}
