package com.phananh.ArenaFlow.service;

import com.phananh.ArenaFlow.dto.response.AuthResponse;

/** Kết quả đăng ký-hoặc-đăng nhập: controller chọn 201 (isNewUser) hoặc 200. */
public record AuthResult(AuthResponse body, boolean isNewUser) {
}
