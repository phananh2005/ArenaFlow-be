package com.phananh.arenaflow.api.config;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalDateTimeParamConverterTest {

    private final LocalDateTimeParamConverter converter = new LocalDateTimeParamConverter();

    @Test
    void acceptsIsoLocalDateTimeWithoutOffset() {
        assertThat(converter.convert("2026-11-12T00:00:00")).isEqualTo(LocalDateTime.of(2026, 11, 12, 0, 0));
        assertThat(converter.convert("2026-11-12T18:30")).isEqualTo(LocalDateTime.of(2026, 11, 12, 18, 30));
        assertThat(converter.convert("2026-11-12T18:30:00.123456"))
                .isEqualTo(LocalDateTime.of(2026, 11, 12, 18, 30, 0, 123_456_000));
    }

    @Test
    void rejectsValuesWithZoneOrOffset() {
        assertThatThrownBy(() -> converter.convert("2026-11-12T00:00:00Z")).isInstanceOf(DateTimeParseException.class);
        assertThatThrownBy(() -> converter.convert("2026-11-12T00:00:00+07:00"))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void blankIsNull() {
        assertThat(converter.convert("  ")).isNull();
    }
}
