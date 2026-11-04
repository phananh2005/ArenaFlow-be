package com.phananh.arenaflow.domain.entity.team;

import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillLevelsConverterTest {

    private final SkillLevelsConverter converter = new SkillLevelsConverter();

    @Test
    void convertsSkillLevelsToJsonAndBack() {
        List<String> skills = List.of("Nhiệt tình", "Rèn luyện");

        String databaseValue = converter.convertToDatabaseColumn(skills);

        assertThat(databaseValue).isEqualTo("[\"Nhiệt tình\",\"Rèn luyện\"]");
        assertThat(converter.convertToEntityAttribute(databaseValue)).isEqualTo(skills);
    }

    @Test
    void convertsNullAndEmptyValuesToEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
        assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }
}
