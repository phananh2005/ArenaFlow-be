package com.phananh.arenaflow.application.support.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.phananh.arenaflow.application.support.security.JwtProperties;
import com.phananh.arenaflow.domain.entity.user.User;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenService {

    private final byte[] secret;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    @Autowired
    public JwtTokenService(JwtProperties properties) {
        this(properties.secretBytes(), properties.accessTokenTtl(), properties.refreshTokenTtl());
    }

    public JwtTokenService(byte[] secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
        if (secret.length < JwtProperties.MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("JWT secret must contain at least 256 bits");
        }
        this.secret = secret.clone();
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    /** Tạo access token HS256 với claim typ=access và role của user. */
    public String issueAccessToken(UUID userId, User.Role role) {
        return issueToken(userId, "access", List.of(role.name()), accessTokenTtl);
    }

    /** Tạo refresh token HS256 với claim typ=refresh và {@code roles} rỗng (role đọc lại từ DB khi refresh). */
    public String issueRefreshToken(UUID userId) {
        return issueToken(userId, "refresh", List.of(), refreshTokenTtl);
    }

    /** Xác minh chữ ký, type, expiry và subject UUID; token sai/hết hạn → AUTH_002. */
    public UUID parseRefreshToken(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secret))) {
                throw new ParseException("Invalid JWT signature", 0);
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            if (!"refresh".equals(claims.getStringClaim("typ"))
                    || claims.getExpirationTime() == null
                    || !claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
                throw new ParseException("Invalid refresh token", 0);
            }
            return UUID.fromString(claims.getSubject());
        } catch (ParseException | JOSEException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.AUTH_002);
        }
    }

    private String issueToken(UUID userId, String type, List<String> roles, Duration ttl) {
        Instant issuedAt = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.toString())
                .claim("roles", roles)
                .claim("typ", type)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plus(ttl)))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            signedJwt.sign(new MACSigner(secret));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Could not sign JWT", exception);
        }
    }
}
