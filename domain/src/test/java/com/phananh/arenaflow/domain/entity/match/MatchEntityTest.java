package com.phananh.arenaflow.domain.entity.match;

import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Version;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MatchEntityTest {

    @Test
    void builderDefaultsSkillLevelsToEmptyList() {
        Match match = Match.builder().build();

        assertThat(match.getSkillLevels()).isEmpty();
    }

    @Test
    void enumsMatchApiValues() {
        assertThat(Match.Format.values()).extracting(Enum::name)
                .containsExactly("FIVE_A_SIDE", "SEVEN_A_SIDE", "ELEVEN_A_SIDE");
        assertThat(Match.CostType.values()).extracting(Enum::name)
                .containsExactly("VENUE_ONLY", "DRINKS_ONLY", "BOTH");
        assertThat(Match.CostSplit.values()).extracting(Enum::name)
                .containsExactly("FIFTY_FIFTY", "SEVENTY_THIRTY_HOME", "SEVENTY_THIRTY_LOSER", "LOSER_PAYS_DRINKS");
    }

    @Test
    void versionIsOptimisticLockAndSkillLevelsUseConverter() throws NoSuchFieldException {
        Field version = Match.class.getDeclaredField("version");
        assertThat(version.isAnnotationPresent(Version.class)).isTrue();

        Field skillLevels = Match.class.getDeclaredField("skillLevels");
        assertThat(skillLevels.getAnnotation(Convert.class).converter()).isEqualTo(SkillLevelsConverter.class);
        assertThat(skillLevels.getAnnotation(Column.class).name()).isEqualTo("skill_levels");

        assertThat(Match.class.getDeclaredField("format").getAnnotation(Column.class).length()).isEqualTo(20);
        assertThat(Match.class.getDeclaredField("cancelReason").getAnnotation(Column.class).length()).isEqualTo(500);
    }

    @Test
    void updatedAtHasSetterSoCostAgreementMakesEntityDirty() {
        Match match = Match.builder().build();
        LocalDateTime at = LocalDateTime.of(2026, 11, 21, 10, 0);

        match.setUpdatedAt(at);

        assertThat(match.getUpdatedAt()).isEqualTo(at);
    }

    @Test
    void matchRequestHasReasonAndCostItemHasDisplayOrder() throws NoSuchFieldException {
        MatchRequest request = MatchRequest.builder().reason("Đã có lịch").build();
        CostCustomItem item = CostCustomItem.builder().displayOrder(2).build();

        assertThat(request.getReason()).isEqualTo("Đã có lịch");
        assertThat(item.getDisplayOrder()).isEqualTo(2);
        assertThat(CostCustomItem.class.getDeclaredField("displayOrder").getAnnotation(Column.class).name())
                .isEqualTo("display_order");
    }
}
