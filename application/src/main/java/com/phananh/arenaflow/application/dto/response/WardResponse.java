package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

public record WardResponse(UUID wardId, String wardName, UUID provinceId, String provinceName) {
}
