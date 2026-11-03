package com.phananh.ArenaFlow.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // allow-secret: fixture giả chỉ dùng cho test

    @Test
    void decodesBase64SecretAndKeepsConfiguredTtls() {
        JwtProperties properties = new JwtProperties(SECRET, Duration.ofMinutes(30), Duration.ofDays(1));

        assertThat(properties.secretBytes()).containsExactly("0123456789abcdef0123456789abcdef".getBytes());
        assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void defaultsTtlsToOneHourAndSevenDays() {
        JwtProperties properties = new JwtProperties(SECRET, null, null);

        assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.refreshTokenTtl()).isEqualTo(Duration.ofDays(7));
    }

    @Test
    void rejectsMissingOrShortSecret() {
        String shortSecret = Base64.getEncoder().encodeToString(new byte[31]);

        assertThatThrownBy(() -> new JwtProperties(" ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(shortSecret, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
