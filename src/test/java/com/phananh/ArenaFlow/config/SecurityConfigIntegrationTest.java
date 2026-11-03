package com.phananh.ArenaFlow.config;

import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.security.CurrentUser;
import com.phananh.ArenaFlow.security.JwtTokenService;
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
