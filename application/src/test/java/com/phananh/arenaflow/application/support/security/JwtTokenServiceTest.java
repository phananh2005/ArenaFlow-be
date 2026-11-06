package com.phananh.arenaflow.application.support.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.phananh.arenaflow.application.support.security.JwtProperties;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final byte[] SECRET = Base64.getDecoder().decode(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    private final JwtTokenService service = new JwtTokenService(
            SECRET, Duration.ofHours(1), Duration.ofDays(7));

    @Test
    void issuesAccessTokenWithUserRoleAndOneHourExpiry() throws ParseException {
        UUID userId = UUID.randomUUID();

        SignedJWT token = SignedJWT.parse(service.issueAccessToken(userId, User.Role.USER));

        assertThat(token.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(token.getJWTClaimsSet().getStringClaim("typ")).isEqualTo("access");
        assertThat(token.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("USER");
        assertThat(token.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(token.getJWTClaimsSet().getIssueTime().toInstant().plus(Duration.ofHours(1)));
    }

    @Test
    void usesSecretAndTtlsFromJwtProperties() throws ParseException {
        JwtProperties properties = new JwtProperties(
                Base64.getEncoder().encodeToString(SECRET), Duration.ofMinutes(15), null);
        JwtTokenService configured = new JwtTokenService(properties);
        UUID userId = UUID.randomUUID();

        SignedJWT access = SignedJWT.parse(configured.issueAccessToken(userId, User.Role.ADMIN));
        SignedJWT refresh = SignedJWT.parse(configured.issueRefreshToken(userId));

        assertThat(access.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(access.getJWTClaimsSet().getIssueTime().toInstant().plus(Duration.ofMinutes(15)));
        assertThat(refresh.getJWTClaimsSet().getExpirationTime().toInstant())
                .isEqualTo(refresh.getJWTClaimsSet().getIssueTime().toInstant().plus(Duration.ofDays(7)));
        assertThat(service.parseRefreshToken(configured.issueRefreshToken(userId))).isEqualTo(userId);
    }

    @Test
    void parsesRefreshTokenAndRejectsAccessTokenAsRefresh() {
        UUID userId = UUID.randomUUID();
        String refreshToken = service.issueRefreshToken(userId);

        assertThat(service.parseRefreshToken(refreshToken)).isEqualTo(userId);
        assertThatThrownBy(() -> service.parseRefreshToken(service.issueAccessToken(userId, User.Role.USER)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_002));
    }

    @Test
    void rejectsSignedRefreshTokenWithoutSubjectAsAuth002() throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("typ", "refresh")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(Duration.ofDays(1))))
                .build();
        SignedJWT token = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        token.sign(new MACSigner(SECRET));

        assertThatThrownBy(() -> service.parseRefreshToken(token.serialize()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.AUTH_002));
    }
}
