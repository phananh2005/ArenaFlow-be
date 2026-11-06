package com.phananh.arenaflow.common.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String details,
        OffsetDateTime timestamp,
        String path,
        List<ValidationError> validationErrors) {

    /**
     * Lỗi không kèm danh sách field: {@code validationErrors} là list rỗng (không null), {@code timestamp} = thời
     * điểm gọi.
     */
    public static ErrorResponse from(ErrorCode errorCode, String details, String path) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), details,
                OffsetDateTime.now(), path, List.of());
    }
}
