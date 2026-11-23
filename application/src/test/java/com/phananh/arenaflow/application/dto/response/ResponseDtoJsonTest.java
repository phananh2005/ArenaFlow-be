package com.phananh.arenaflow.application.dto.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** JSON của response DTO: tên field và định dạng {@code LocalDateTime} không kèm múi giờ. */
class ResponseDtoJsonTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @Test
    void authResponseUsesIsNewUserFieldName() {
        AuthResponse response = new AuthResponse(UUID.randomUUID(), "USER", true, "a", "r", "Bearer", 3600);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.has("isNewUser")).isTrue();
        assertThat(json.get("isNewUser").asBoolean()).isTrue();
        assertThat(json.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(json.get("expiresIn").asLong()).isEqualTo(3600);
    }

    @Test
    void profileResponseKeepsNullWardAndWritesIsoTimestamps() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 11, 1, 17, 0);
        ProfileResponse response = new ProfileResponse(UUID.randomUUID(), "USER",
                List.of(new ProviderResponse("GOOGLE", "user@example.com")), null, "User 1a2b3c",
                null, null, null, null, null, null, createdAt, createdAt);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.has("ward")).isTrue();
        assertThat(json.get("ward").isNull()).isTrue();
        assertThat(json.get("providers").get(0).get("provider").asString()).isEqualTo("GOOGLE");
        assertThat(json.get("createdAt").asString()).isEqualTo("2026-11-01T17:00:00");
    }

    @Test
    void localDateTimeIsWrittenWithoutOffsetAndTrimsTrailingZeroFraction() {
        assertThat(createdAtJson(LocalDateTime.of(2026, 11, 20, 19, 0))).isEqualTo("2026-11-20T19:00:00");
        assertThat(createdAtJson(LocalDateTime.of(2026, 11, 20, 19, 0, 0, 123_456_000)))
                .isEqualTo("2026-11-20T19:00:00.123456");
        assertThat(createdAtJson(LocalDateTime.of(2026, 11, 20, 19, 0, 0, 500_000_000)))
                .isEqualTo("2026-11-20T19:00:00.5");
        assertThat(createdAtJson(LocalDateTime.of(2026, 11, 20, 19, 0, 0, 120_000_000)))
                .isEqualTo("2026-11-20T19:00:00.12");
    }

    private String createdAtJson(LocalDateTime createdAt) {
        ProfileResponse response = new ProfileResponse(UUID.randomUUID(), "USER", List.of(), null, "User",
                null, null, null, null, null, null, createdAt, createdAt);
        return jsonMapper.valueToTree(response).get("createdAt").asString();
    }
}
