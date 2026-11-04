package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** role USER|ADMIN; tokenType "Bearer"; expiresIn = TTL access token (giây). */
public record AuthResponse(UUID userId, String role, boolean isNewUser, String accessToken, String refreshToken,
                           String tokenType, long expiresIn) {
}
