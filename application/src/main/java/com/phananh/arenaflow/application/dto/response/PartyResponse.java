package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Một bên của cuộc trò chuyện; {@code captainName} chỉ bên TEAM (đội trưởng hiện tại; null khi đội đã giải tán). */
public record PartyResponse(String partyType, UUID partyId, String name, String avatarUrl, String captainName) {
}
