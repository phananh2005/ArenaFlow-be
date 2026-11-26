package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Uy tín đội tính khi đọc: điểm, huy hiệu, trận hoàn thành / được tính, sao trung bình chưa làm tròn. */
public interface TeamReputationView {

    UUID getTeamId();

    Integer getReliabilityScore();

    LocalDateTime getVerifiedAt();

    Long getCompletedMatches();

    Long getCountedMatches();

    BigDecimal getAverageRating();

    Long getRatingCount();
}
