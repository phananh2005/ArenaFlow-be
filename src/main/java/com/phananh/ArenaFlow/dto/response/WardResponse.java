package com.phananh.ArenaFlow.dto.response;

import java.util.UUID;

public record WardResponse(UUID wardId, String wardName, UUID provinceId, String provinceName) {
}
