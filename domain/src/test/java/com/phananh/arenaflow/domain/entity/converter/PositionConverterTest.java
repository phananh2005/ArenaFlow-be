package com.phananh.arenaflow.domain.entity.converter;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment.Position;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PositionConverterTest {

    private final PositionConverter converter = new PositionConverter();

    @Test
    void writesVietnameseLabelsMatchingDatabaseCheck() {
        assertThat(converter.convertToDatabaseColumn(Position.GOALKEEPER)).isEqualTo("Thủ môn");
        assertThat(converter.convertToDatabaseColumn(Position.DEFENDER)).isEqualTo("Hậu vệ");
        assertThat(converter.convertToDatabaseColumn(Position.MIDFIELDER)).isEqualTo("Tiền vệ");
        assertThat(converter.convertToDatabaseColumn(Position.FORWARD)).isEqualTo("Tiền đạo");
    }

    @Test
    void readsLabelsBackToEnum() {
        for (Position position : Position.values()) {
            assertThat(converter.convertToEntityAttribute(position.label())).isEqualTo(position);
        }
    }

    @Test
    void nullStaysNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void unknownLabelIsRejected() {
        assertThatThrownBy(() -> converter.convertToEntityAttribute("Thủ_môn"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Position.fromLabel("Libero")).isInstanceOf(IllegalArgumentException.class);
    }
}
