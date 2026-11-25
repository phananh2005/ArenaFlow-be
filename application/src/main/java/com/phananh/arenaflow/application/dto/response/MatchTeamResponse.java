package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

public record MatchTeamResponse(UUID teamId, String teamName, String avatarUrl, Integer reliabilityScore) {
}
