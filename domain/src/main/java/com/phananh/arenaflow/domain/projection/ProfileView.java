package com.phananh.arenaflow.domain.projection;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Hồ sơ user chưa xoá + phường/xã + tỉnh/thành (1 truy vấn native).
 * Cột thời điểm ({@code createdAt}, {@code updatedAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code
 * LocalDateTime}.
 */
public interface ProfileView {
    UUID getUserId();
    String getRole();
    String getPhoneNumber();
    String getDisplayName();
    String getAvatarUrl();
    String getBio();
    String getPreferredPosition();
    LocalDate getDateOfBirth();
    String getGender();
    UUID getWardId();
    String getWardName();
    UUID getProvinceId();
    String getProvinceName();
    LocalDateTime getCreatedAt();
    LocalDateTime getUpdatedAt();
}
