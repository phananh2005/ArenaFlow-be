package com.phananh.arenaflow.api.config;

import com.phananh.arenaflow.api.ApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JsonMapper do Spring Boot dựng phải áp các customizer đọc chặt (LocalDateTime, số nguyên). */
class JsonMapperIntegrationTest extends ApiIntegrationTest {

    @Autowired
    private JsonMapper jsonMapper;

    /** JsonMapper do Spring Boot dựng phải áp customizer đọc LocalDateTime chặt: chuỗi kèm Z / offset bị từ chối. */
    @Test
    void applicationJsonMapperRejectsLocalDateTimeWithOffset() {
        assertThat(jsonMapper.readValue("\"2026-11-20T19:00:00\"", LocalDateTime.class))
                .isEqualTo(LocalDateTime.of(2026, 11, 20, 19, 0));
        assertThatThrownBy(() -> jsonMapper.readValue("\"2026-11-20T12:00:00Z\"", LocalDateTime.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> jsonMapper.readValue("\"2026-11-20T19:00:00+07:00\"", LocalDateTime.class))
                .isInstanceOf(MismatchedInputException.class);
    }

    /** JsonMapper do Spring Boot dựng phải áp customizer số nguyên chặt: số thập phân / chuỗi bị từ chối. */
    @Test
    void applicationJsonMapperRejectsDecimalOrStringForIntegerField() {
        assertThat(jsonMapper.readValue("4", Integer.class)).isEqualTo(4);
        assertThatThrownBy(() -> jsonMapper.readValue("4.5", Integer.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> jsonMapper.readValue("4.0", Long.class))
                .isInstanceOf(MismatchedInputException.class);
        assertThatThrownBy(() -> jsonMapper.readValue("\"4\"", Integer.class))
                .isInstanceOf(MismatchedInputException.class);
    }
}
