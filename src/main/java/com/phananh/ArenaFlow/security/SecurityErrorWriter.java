package com.phananh.ArenaFlow.security;

import com.phananh.ArenaFlow.exception.ErrorCode;
import com.phananh.ArenaFlow.exception.ErrorEnvelope;
import com.phananh.ArenaFlow.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Ghi envelope lỗi chuẩn cho các lỗi phát sinh ở tầng filter Spring Security (ngoài GlobalExceptionHandler). */
final class SecurityErrorWriter {

    private SecurityErrorWriter() {
    }

    static void write(JsonMapper jsonMapper, HttpServletRequest request, HttpServletResponse response,
                      ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorEnvelope body = new ErrorEnvelope(ErrorResponse.from(errorCode, null, request.getRequestURI()));
        jsonMapper.writeValue(response.getOutputStream(), body);
    }
}
