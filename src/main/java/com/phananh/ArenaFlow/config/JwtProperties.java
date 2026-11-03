package com.phananh.ArenaFlow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Base64;

@ConfigurationProperties(prefix = "arenaflow.jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must be configured");
        }
        if (Base64.getDecoder().decode(secret).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must contain at least 256 bits");
        }
        accessTokenTtl = accessTokenTtl == null ? Duration.ofHours(1) : accessTokenTtl;
        refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
    }

    public byte[] secretBytes() {
        return Base64.getDecoder().decode(secret);
    }
}
