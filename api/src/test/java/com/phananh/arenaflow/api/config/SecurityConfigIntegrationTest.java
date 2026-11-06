package com.phananh.arenaflow.api.config;

import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.api.security.CurrentUser;
import com.phananh.arenaflow.application.support.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Import(SecurityConfigIntegrationTest.ProbeController.class)
class SecurityConfigIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenService tokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void rejectsMissingTokenWithAuth001() throws Exception {
        mockMvc.perform(get("/api/v1/probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void rejectsRefreshTokenUsedAsBearerWithAuth001() throws Exception {
        String refresh = tokenService.issueRefreshToken(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/probe/me").header("Authorization", "Bearer " + refresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @Test
    void resolvesCurrentUserFromAccessToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String access = tokenService.issueAccessToken(userId, User.Role.USER);

        mockMvc.perform(get("/api/v1/probe/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(content().string(userId.toString()));
    }

    @Test
    void forbidsUserOnAdminPathWithAuth004() throws Exception {
        String access = tokenService.issueAccessToken(UUID.randomUUID(), User.Role.USER);

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_004"));
    }

    @Test
    void allowsAdminOnAdminPath() throws Exception {
        String access = tokenService.issueAccessToken(UUID.randomUUID(), User.Role.ADMIN);

        mockMvc.perform(get("/api/v1/admin/probe").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
    }

    @Test
    void permitsAuthPathsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/probe"))
                .andExpect(status().isOk());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    /**
     * /actuator/health/** public (không 401) nhưng thành phần bị ẩn theo mặc định Spring Boot → 404,
     * không lộ trạng thái DB. permitAll dành cho probe liveness/readiness khi được bật.
     */
    @Test
    void healthComponentPathIsPublicButHidden() throws Exception {
        mockMvc.perform(get("/actuator/health/db"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    /** API docs mặc định TẮT (fail-closed) — chỉ bật khi SPRINGDOC_ENABLED=true (dev). */
    @Test
    void apiDocsAreDisabledByDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }

    @Test
    void otherActuatorEndpointsStillRequireToken() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_001"));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/probe/me")
        String me(@CurrentUser UUID currentUserId) {
            return currentUserId.toString();
        }

        @GetMapping("/api/v1/admin/probe")
        String admin() {
            return "admin";
        }

        @GetMapping("/api/v1/auth/probe")
        String auth() {
            return "public";
        }
    }
}
