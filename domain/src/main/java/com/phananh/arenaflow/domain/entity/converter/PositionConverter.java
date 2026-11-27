package com.phananh.arenaflow.domain.entity.converter;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment.Position;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Vị trí ↔ nhãn tiếng Việt lưu trong cột {@code position} (khớp CHECK của bảng); nhãn lạ → IllegalArgumentException.
 */
@Converter
public class PositionConverter implements AttributeConverter<Position, String> {

    @Override
    public String convertToDatabaseColumn(Position position) {
        return position == null ? null : position.label();
    }

    @Override
    public Position convertToEntityAttribute(String label) {
        return label == null ? null : Position.fromLabel(label);
    }
}
