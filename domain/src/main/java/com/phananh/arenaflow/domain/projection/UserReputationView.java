package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uy tín cá nhân: điểm, huy hiệu, thống kê tính khi đọc (chưa làm tròn). */
public interface UserReputationView {
    UUID getUserId();
    Boolean getDeleted();
    Integer getReliabilityScore();
    LocalDateTime getVerifiedAt();
    Long getCompletedMatches();
    Long getCountedMatches();
    BigDecimal getAverageRating();
    Long getRatingCount();
}
