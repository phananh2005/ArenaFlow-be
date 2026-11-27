package com.phananh.arenaflow.domain.entity.mercenary;

import com.phananh.arenaflow.domain.entity.converter.PositionConverter;
import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;
import com.phananh.arenaflow.domain.entity.rating.Rating;
import com.phananh.arenaflow.domain.entity.rating.Report;
import com.phananh.arenaflow.domain.entity.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MercenaryEntityTest {

    @Test
    void recruitmentDefaultsToOpenAndUsesPositionConverter() throws Exception {
        MercenaryRecruitment recruitment = MercenaryRecruitment.builder()
                .position(MercenaryRecruitment.Position.MIDFIELDER).quantityNeeded(2).build();

        assertThat(recruitment.getStatus()).isEqualTo(MercenaryRecruitment.Status.OPEN);
        assertThat(recruitment.getQuantityApproved()).isZero();
        Field position = MercenaryRecruitment.class.getDeclaredField("position");
        assertThat(position.getAnnotation(Convert.class).converter()).isEqualTo(PositionConverter.class);
    }

    @Test
    void recruitmentStateColumnsChangeOnlyThroughConditionalUpdates() throws Exception {
        for (String field : List.of("status", "quantityNeeded", "quantityApproved", "closedAt", "createdByUserId",
                "matchId", "externalMatchId", "teamId", "position")) {
            assertThat(column(MercenaryRecruitment.class, field).updatable()).as(field).isFalse();
        }
        assertThat(column(MercenaryRecruitment.class, "matchId").nullable()).isTrue();
        assertThat(column(MercenaryRecruitment.class, "createdByUserId").nullable()).isFalse();
    }

    @Test
    void recruitmentTimestampsUseLocalDateTime() {
        MercenaryRecruitment recruitment = MercenaryRecruitment.builder().build();
        recruitment.onCreate();

        assertThat(recruitment.getCreatedAt()).isInstanceOf(LocalDateTime.class);
        assertThat(recruitment.getUpdatedAt()).isEqualTo(recruitment.getCreatedAt());
    }

    @Test
    void applicationDefaultsToPendingAndStateColumnsAreReadOnly() throws Exception {
        MercenaryApplication application = MercenaryApplication.builder().build();
        application.onCreate();

        assertThat(application.getStatus()).isEqualTo(MercenaryApplication.Status.PENDING);
        assertThat(application.getAppliedAt()).isNotNull();
        for (String field : List.of("status", "respondedAt", "withdrawnAt")) {
            assertThat(column(MercenaryApplication.class, field).updatable()).as(field).isFalse();
        }
        Column notice = column(MercenaryApplication.class, "gameCancelNotifiedAt");
        assertThat(notice.insertable()).isFalse();
        assertThat(notice.updatable()).isFalse();
    }

    @Test
    void externalMatchIsVersionedAndInsertOnly() throws Exception {
        ExternalMatch match = ExternalMatch.builder().build();
        match.onCreate();

        assertThat(match.getStatus()).isEqualTo(ExternalMatch.Status.SCHEDULED);
        assertThat(match.getSkillLevels()).isEmpty();
        assertThat(match.getCreatedAt()).isNotNull();
        assertThat(ExternalMatch.class.getDeclaredField("version").getAnnotation(Version.class)).isNotNull();
        assertThat(ExternalMatch.class.getDeclaredField("skillLevels").getAnnotation(Convert.class).converter())
                .isEqualTo(SkillLevelsConverter.class);
        for (String field : List.of("teamId", "venueId", "matchStartTime", "matchEndTime", "skillLevels",
                "opponentName", "status", "cancelledAt")) {
            assertThat(column(ExternalMatch.class, field).updatable()).as(field).isFalse();
        }
    }

    @Test
    void ratingAndReportCarryGameAndApplicationKeys() throws Exception {
        for (Class<?> type : List.of(Rating.class, Report.class)) {
            assertThat(column(type, "matchId").nullable()).isTrue();
            assertThat(column(type, "externalMatchId").updatable()).isFalse();
            assertThat(column(type, "applicationId").updatable()).isFalse();
        }
    }

    @Test
    void userVerifiedAtIsWrittenOnlyByRefreshQuery() throws Exception {
        Column verifiedAt = column(User.class, "verifiedAt");

        assertThat(verifiedAt.insertable()).isFalse();
        assertThat(verifiedAt.updatable()).isFalse();
    }

    private static Column column(Class<?> type, String field) throws NoSuchFieldException {
        return type.getDeclaredField(field).getAnnotation(Column.class);
    }
}
