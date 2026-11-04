package com.phananh.arenaflow.domain.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Converter
public class SkillLevelsConverter implements AttributeConverter<List<String>, String> {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final TypeReference<List<String>> SKILL_LEVELS_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return JSON_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize skill levels", exception);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String databaseValue) {
        if (databaseValue == null || databaseValue.isBlank()) {
            return List.of();
        }
        try {
            return JSON_MAPPER.readValue(databaseValue, SKILL_LEVELS_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not deserialize skill levels", exception);
        }
    }
}
