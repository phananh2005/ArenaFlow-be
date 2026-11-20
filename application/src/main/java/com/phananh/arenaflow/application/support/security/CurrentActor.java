package com.phananh.arenaflow.application.support.security;

import java.util.UUID;

/** Danh tính caller đã xác thực và vai trò Admin, lấy từ claim của access token. */
public record CurrentActor(UUID userId, boolean admin) {
}
