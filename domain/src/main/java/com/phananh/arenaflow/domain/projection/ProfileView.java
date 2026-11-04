package com.phananh.arenaflow.domain.projection;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

/** Hồ sơ user chưa xoá + phường/xã + tỉnh/thành (1 truy vấn native). */
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
    Instant getCreatedAt();
    Instant getUpdatedAt();
}
