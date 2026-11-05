package com.phananh.arenaflow.api.auth;

import com.phananh.arenaflow.api.ApiIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest extends ApiIntegrationTest {

    private static String oauthBody(String provider, String email) {
        return "{\"provider\":\"" + provider + "\",\"oauthToken\":\"token\",\"email\":\"" + email + "\"}";
    }

    @Test
    void oauthRegistersWith201ThenLogsInWith200() throws Exception {
        String email = "api-" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(json(post("/api/v1/auth/oauth"), oauthBody("GOOGLE", email)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        mockMvc.perform(json(post("/api/v1/auth/oauth"), oauthBody("GOOGLE", email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isNewUser").value(false));
    }

    @Test
    void oauthRejectsInvalidBodyAndMalformedJsonWith400() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/oauth"), oauthBody("PHONE", "not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.validationErrors[*].field", hasItems("provider", "email")));

        mockMvc.perform(json(post("/api/v1/auth/oauth"), "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void oauthForDeletedAccountReturns403Auth003() throws Exception {
        String email = "deleted-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(json(post("/api/v1/auth/oauth"), oauthBody("FACEBOOK", email)))
                .andExpect(status().isCreated());
        flush();
        entityManager.createNativeQuery("UPDATE \"user\" SET deleted_at = :now WHERE id = "
                        + "(SELECT user_id FROM oauth_account WHERE provider_user_id = :email)")
                .setParameter("now", OffsetDateTime.now()).setParameter("email", email).executeUpdate();

        mockMvc.perform(json(post("/api/v1/auth/oauth"), oauthBody("FACEBOOK", email)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("AUTH_003"));
    }

    @Test
    void otpFlow() throws Exception {
        mockMvc.perform(json(post("/api/v1/auth/send-otp"), "{\"phone\":\"0987654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("0987654321"))
                .andExpect(jsonPath("$.data.expiresIn").value(300));
        mockMvc.perform(json(post("/api/v1/auth/send-otp"), "{\"phone\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        String phone = "09" + String.format("%08d", Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000));
        mockMvc.perform(json(post("/api/v1/auth/verify-otp"), "{\"phone\":\"" + phone + "\",\"otp\":\"123456\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.data.isNewUser").value(true));
    }

    @Test
    void refreshIssuesNewPairOrRejectsWithAuth002() throws Exception {
        var user = saveUser("Refresh User");
        flush();
        String refresh = jwtTokenService.issueRefreshToken(user.getId());

        mockMvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId().toString()))
                .andExpect(jsonPath("$.data.isNewUser").value(false));

        mockMvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"garbage\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_002"));
    }

    /** Endpoint auth là public: client gửi kèm access token hết hạn/sai vẫn phải refresh được. */
    @Test
    void publicAuthEndpointIgnoresInvalidBearerHeader() throws Exception {
        var user = saveUser("Expired Bearer");
        flush();
        String refresh = jwtTokenService.issueRefreshToken(user.getId());

        mockMvc.perform(json(post("/api/v1/auth/refresh"), "{\"refreshToken\":\"" + refresh + "\"}")
                        .header("Authorization", "Bearer garbage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(user.getId().toString()));
    }
}
