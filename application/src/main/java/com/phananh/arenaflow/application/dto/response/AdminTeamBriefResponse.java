package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Đội rút gọn ở API Admin (kèm trạng thái đội). */
public record AdminTeamBriefResponse(UUID teamId, String name, String avatarUrl, String status) {
}
