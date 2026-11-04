package com.phananh.arenaflow.application.service;

import com.phananh.arenaflow.application.dto.response.AuthResponse;

/** Kết quả đăng ký-hoặc-đăng nhập: controller chọn 201 (isNewUser) hoặc 200. */
public record AuthResult(AuthResponse body, boolean isNewUser) {
}
