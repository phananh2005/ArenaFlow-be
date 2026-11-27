package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Dòng tin vừa tạo (INSERT … RETURNING). */
public interface RecruitmentRowView {
    UUID getId();
    String getPosition();
    Integer getQuantityNeeded();
    LocalDateTime getCreatedAt();
}
