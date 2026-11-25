package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

public record CostCustomItemResponse(UUID itemId, String itemName, Long amount) {
}
