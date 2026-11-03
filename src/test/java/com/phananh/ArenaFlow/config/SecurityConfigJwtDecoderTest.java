package com.phananh.ArenaFlow.config;

import com.phananh.ArenaFlow.entity.user.User;
import com.phananh.ArenaFlow.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityConfigJwtDecoderTest {

    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="; // allow-secret: fixture giả chỉ dùng cho test
    private static final String OTHER_SECRET = Base64.getEncoder()
            .encodeToString("another-secret-another-secret-32".getBytes());

    private final JwtProperties properties = new JwtProperties(SECRET, Duration.ofHours(1), Duration.ofDays(7));
    private final JwtTokenService tokenService = new JwtTokenService(properties);
    private final JwtDecoder decoder = new SecurityConfig().jwtDecoder(properties);

    @Test
    void decodesAccessTokenIssuedWithSameSecret() {
        UUID userId = UUID.randomUUID();

        Jwt jwt = decoder.decode(tokenService.issueAccessToken(userId, User.Role.USER));

        assertThat(jwt.getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
    }

    @Test
    void rejectsRefreshTokenUsedAsAccessToken() {
        String refreshToken = tokenService.issueRefreshToken(UUID.randomUUID());

        assertThatThrownBy(() -> decoder.decode(refreshToken)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithAnotherSecret() {
        JwtTokenService otherIssuer = new JwtTokenService(
                new JwtProperties(OTHER_SECRET, Duration.ofHours(1), Duration.ofDays(7)));
        String foreignToken = otherIssuer.issueAccessToken(UUID.randomUUID(), User.Role.ADMIN);

        assertThatThrownBy(() -> decoder.decode(foreignToken)).isInstanceOf(JwtException.class);
    }
}
