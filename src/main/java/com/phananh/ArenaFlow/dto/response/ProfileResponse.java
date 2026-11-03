package com.phananh.ArenaFlow.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** §3.11 — ward = null khi chưa chọn. */
public record ProfileResponse(UUID userId, String role, List<ProviderResponse> providers, String phoneNumber,
                              String displayName, String avatarUrl, String bio, String preferredPosition,
                              LocalDate dateOfBirth, String gender, WardResponse ward,
                              OffsetDateTime createdAt, OffsetDateTime updatedAt) {
}
