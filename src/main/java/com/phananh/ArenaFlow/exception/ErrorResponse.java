package com.phananh.ArenaFlow.exception;

import java.time.OffsetDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String details,
        OffsetDateTime timestamp,
        String path,
        List<ValidationError> validationErrors) {

    public static ErrorResponse from(ErrorCode errorCode, String details, String path) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage(), details,
                OffsetDateTime.now(), path, List.of());
    }
}
