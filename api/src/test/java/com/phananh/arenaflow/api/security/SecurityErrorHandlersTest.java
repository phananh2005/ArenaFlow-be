package com.phananh.arenaflow.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Handler lỗi bảo mật ghi thân lỗi chuẩn cho 401 và 403 (thời điểm không kèm múi giờ). */
class SecurityErrorHandlersTest {

    private static final String LOCAL_DATE_TIME = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,6})?$";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void entryPointWritesUnauthorizedErrorEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAuthenticationEntryPoint(jsonMapper)
                .commence(request, response, new BadCredentialsException("bad token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode error = jsonMapper.readTree(response.getContentAsString()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("AUTH_001");
        assertThat(error.get("path").asString()).isEqualTo("/api/v1/users/me");
        // Giờ Việt Nam, không có Z / offset.
        assertThat(error.get("timestamp").asString()).matches(LOCAL_DATE_TIME);
    }

    @Test
    void accessDeniedHandlerWritesForbiddenErrorEnvelope() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/teams");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RestAccessDeniedHandler(jsonMapper)
                .handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        JsonNode error = jsonMapper.readTree(response.getContentAsString()).get("error");
        assertThat(error.get("code").asString()).isEqualTo("AUTH_004");
        assertThat(error.get("path").asString()).isEqualTo("/api/v1/admin/teams");
        assertThat(error.get("timestamp").asString()).matches(LOCAL_DATE_TIME);
    }
}
