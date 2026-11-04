package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.validation.MinAge;
import com.phananh.arenaflow.application.validation.OneOf;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;
import java.util.UUID;

/** PATCH /profile: mọi field tuỳ chọn; null = giữ nguyên. */
public record ProfileUpdateRequest(
        @Size(max = 100) @Pattern(regexp = ".*\\S.*", message = "Không được để trống") String displayName,
        @URL(regexp = "^https?://.*") @Size(max = 500) String avatarUrl,
        @Size(max = 1000) String bio,
        @OneOf({"Thủ môn", "Hậu vệ", "Tiền vệ", "Tiền đạo"}) String preferredPosition,
        @Past @MinAge(13) LocalDate dateOfBirth,
        @OneOf({"MALE", "FEMALE", "OTHER"}) String gender,
        UUID wardId) {
}
