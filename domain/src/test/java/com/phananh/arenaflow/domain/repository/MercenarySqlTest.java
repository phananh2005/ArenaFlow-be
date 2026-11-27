package com.phananh.arenaflow.domain.repository;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class MercenarySqlTest {

    @Test
    void everyFragmentIsCompileTimeConstantWithoutDatabaseClock() throws Exception {
        for (Field field : fragments()) {
            String sql = ((String) field.get(null)).toLowerCase(Locale.ROOT);
            assertThat(sql).as(field.getName()).doesNotContain("now()").doesNotContain("current_timestamp")
                    .doesNotContain("localtimestamp").doesNotContain("current_date");
        }
    }

    @Test
    void concatenatedFragmentsKeepTokenBoundaries() throws Exception {
        for (Field field : fragments()) {
            String sql = (String) field.get(null);
            assertThat(sql).as(field.getName()).doesNotContain("ENDAS").doesNotContain("ELSECASE")
                    .doesNotContain("ENDEND").doesNotContain(",,");
        }
        assertThat(MercenarySql.RECRUITMENT_COLUMNS).contains("END AS effectiveStatus")
                .contains(" AS skillLevels,").contains("r.status AS status,");
        assertThat(MercenarySql.APPLICATION_COLUMNS).contains("END AS effectiveStatus")
                .contains("END AS recruitmentEffectiveStatus").contains(" AS gameEnd,");
        assertThat(MercenarySql.CONVERSATION_MERCENARY_COLUMNS).contains("ELSE CASE").contains("END AS applicationEffectiveStatus");
    }

    @Test
    void gameExpressionsReadEitherSystemMatchOrExternalMatch() {
        assertThat(MercenarySql.GAME_JOIN).contains("LEFT JOIN match m ON m.id = r.match_id")
                .contains("LEFT JOIN external_match x ON x.id = r.external_match_id");
        assertThat(MercenarySql.GAME_START).isEqualTo("COALESCE(m.match_start_time, x.match_start_time)");
        assertThat(MercenarySql.GAME_END).isEqualTo("COALESCE(m.match_end_time, x.match_end_time)");
        assertThat(MercenarySql.GAME_CANCELLED).contains("COALESCE(m.status, x.status)").contains("'CANCELLED'");
    }

    @Test
    void effectiveStatusesDeriveClosedExpiredAndRejected() {
        assertThat(MercenarySql.RECRUITMENT_EFFECTIVE_STATUS).contains("THEN 'CLOSED'").contains("THEN 'EXPIRED'")
                .contains(":now");
        assertThat(MercenarySql.APPLICATION_EFFECTIVE_STATUS).contains("a.status = 'PENDING'").contains("'REJECTED'")
                .contains(MercenarySql.RECRUITMENT_EFFECTIVE_STATUS);
    }

    @Test
    void overlapCountsOnlyApprovedApplicationsOfGamesNotCancelled() {
        assertThat(MercenarySql.OVERLAPPING_APPROVAL_OF_CALLER).contains("a2.applicant_id = :callerId")
                .contains("a2.status = 'APPROVED'").contains("<> 'CANCELLED'");
        assertThat(MercenarySql.OVERLAPPING_APPROVAL_OF_APPLICANT).contains("a2.applicant_id = a.applicant_id");
        assertThat(MercenarySql.MEMBER_OF_GAME_TEAMS_CALLER)
                .contains("tm.team_id IN (m.author_team_id, m.confirmed_opponent_team_id, x.team_id)");
    }

    @Test
    void userStatsUsesSubjectAliasAndParameters() {
        assertThat(MercenarySql.USER_STATS_LATERAL).contains("su.id").contains(":cancelWindowHours")
                .contains("COUNT(DISTINCT");
    }

    private static List<Field> fragments() {
        List<Field> result = new ArrayList<>();
        for (Field field : MercenarySql.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())
                    && field.getType() == String.class) {
                result.add(field);
            }
        }
        assertThat(result).isNotEmpty();
        return result;
    }
}
