package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

public record PartyBriefResponse(String partyType, UUID partyId, String name, String avatarUrl) {
}
