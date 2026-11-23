package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ người dùng; ward = null khi chưa chọn.
 *
 * @param userId            id user
 * @param role              {@code USER} hoặc {@code ADMIN}
 * @param providers         các cách đăng nhập đã liên kết
 * @param phoneNumber       số điện thoại; null khi không đăng ký bằng số điện thoại
 * @param displayName       tên hiển thị
 * @param avatarUrl         ảnh đại diện; có thể null
 * @param bio               giới thiệu; có thể null
 * @param preferredPosition vị trí ưa thích; có thể null
 * @param dateOfBirth       ngày sinh; có thể null
 * @param gender            giới tính; có thể null
 * @param ward              phường/xã kèm tỉnh; null khi chưa chọn
 * @param createdAt         thời điểm tạo tài khoản, giờ Việt Nam không kèm múi giờ
 * @param updatedAt         thời điểm sửa gần nhất, giờ Việt Nam không kèm múi giờ
 */
public record ProfileResponse(UUID userId, String role, List<ProviderResponse> providers, String phoneNumber,
                              String displayName, String avatarUrl, String bio, String preferredPosition,
                              LocalDate dateOfBirth, String gender, WardResponse ward,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
}
