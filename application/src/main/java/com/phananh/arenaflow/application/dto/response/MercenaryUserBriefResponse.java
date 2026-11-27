package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/** Người dùng rút gọn (tên / ảnh đã che khi tài khoản đã xoá). */
public record MercenaryUserBriefResponse(UUID userId, String displayName, String avatarUrl) {
}
