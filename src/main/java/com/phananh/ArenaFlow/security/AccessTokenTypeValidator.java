package com.phananh.ArenaFlow.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Chỉ chấp nhận access token ({@code typ=access}) ở resource server; refresh token bị từ chối.
 */
public class AccessTokenTypeValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_TYPE =
            new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Token is not an access token", null);

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        return "access".equals(jwt.getClaimAsString("typ"))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID_TYPE);
    }
}
