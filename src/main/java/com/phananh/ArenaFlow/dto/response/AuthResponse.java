package com.phananh.ArenaFlow.dto.response;

import java.util.UUID;

/** §3.10 — role USER|ADMIN; tokenType "Bearer"; expiresIn = TTL access token (giây). */
public record AuthResponse(UUID userId, String role, boolean isNewUser, String accessToken, String refreshToken,
                           String tokenType, long expiresIn) {
}
