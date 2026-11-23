package com.phananh.arenaflow.common.exception;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Kiểm thân lỗi trả cho client: giữ đủ thông tin lỗi và đóng dấu thời điểm hiện tại cắt về micro giây. */
class ErrorResponseTest {

    @Test
    void storesErrorMetadataAndValidationDetails() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 11, 3, 17, 0);
        ValidationError validationError = new ValidationError("phone", "must match pattern");
        ErrorResponse response = new ErrorResponse(
                "VALIDATION_ERROR", "Dữ liệu không hợp lệ", null, timestamp, "/api/v1/auth/send-otp", List.of(validationError));

        assertThat(response.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.timestamp()).isEqualTo(timestamp);
        assertThat(response.path()).isEqualTo("/api/v1/auth/send-otp");
        assertThat(response.validationErrors()).containsExactly(validationError);
    }

    @Test
    void fromStampsCurrentLocalTimeTruncatedToMicros() {
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);

        ErrorResponse response = ErrorResponse.from(ErrorCode.values()[0], null, "/api/v1/x");

        assertThat(response.timestamp()).isInstanceOf(LocalDateTime.class)
                .isAfter(before)
                .isBefore(LocalDateTime.now().plusSeconds(1));
        assertThat(response.timestamp().getNano() % 1_000).isZero();
    }
}
