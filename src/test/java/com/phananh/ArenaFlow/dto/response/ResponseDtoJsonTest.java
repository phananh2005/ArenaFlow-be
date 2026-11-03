package com.phananh.ArenaFlow.dto.response;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
        ProfileResponse response = new ProfileResponse(UUID.randomUUID(), "USER",
                List.of(new ProviderResponse("GOOGLE", "user@example.com")), null, "User 1a2b3c",
                null, null, null, null, null, null, createdAt, createdAt);

        JsonNode json = jsonMapper.valueToTree(response);

        assertThat(json.has("ward")).isTrue();
        assertThat(json.get("ward").isNull()).isTrue();
        assertThat(json.get("providers").get(0).get("provider").asString()).isEqualTo("GOOGLE");
        assertThat(json.get("createdAt").asString()).startsWith("2026-11-01T10:00");
    }
}
