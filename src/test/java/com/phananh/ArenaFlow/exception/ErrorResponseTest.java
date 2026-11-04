package com.phananh.ArenaFlow.exception;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseTest {

    @Test
    void storesErrorMetadataAndValidationDetails() {
        OffsetDateTime timestamp = OffsetDateTime.parse("2026-11-03T10:00:00Z");
        ValidationError validationError = new ValidationError("phone", "must match pattern");
        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR", "Dữ liệu không hợp lệ", null, timestamp, "/api/v1/auth/send-otp", List.of(validationError));

        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.path()).isEqualTo("/api/v1/auth/send-otp");
        assertThat(response.validationErrors()).containsExactly(validationError);
    }
}
