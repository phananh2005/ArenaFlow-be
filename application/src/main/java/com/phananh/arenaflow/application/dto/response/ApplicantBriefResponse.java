package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Ứng viên rút gọn + vị trí ưa thích. */
public record ApplicantBriefResponse(UUID userId, String displayName, String avatarUrl, String preferredPosition) {
}
