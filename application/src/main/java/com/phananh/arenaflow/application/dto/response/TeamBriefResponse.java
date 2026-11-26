package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

public record TeamBriefResponse(UUID teamId, String name, String avatarUrl) {
}
