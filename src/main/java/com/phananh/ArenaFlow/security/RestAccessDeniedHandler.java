package com.phananh.ArenaFlow.security;

import com.phananh.ArenaFlow.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

/** Đã xác thực nhưng thiếu quyền (vd. USER gọi /api/v1/admin/**) → 403 AUTH_004. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public RestAccessDeniedHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        SecurityErrorWriter.write(jsonMapper, request, response, ErrorCode.AUTH_004);
    }
}
