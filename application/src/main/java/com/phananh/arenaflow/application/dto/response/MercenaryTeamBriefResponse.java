package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Đội rút gọn trong response tuyển mercenary. */
public record MercenaryTeamBriefResponse(UUID teamId, String name, String avatarUrl) {
}
