package com.phananh.arenaflow.domain.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Ứng viên của tin + uy tín cá nhân tính trong cùng câu. */
public interface ApplicantRowView {
    UUID getApplicationId();
    String getEffectiveStatus();
    UUID getConversationId();
    UUID getUserId();
    String getDisplayName();
    String getAvatarUrl();
    Boolean getDeleted();
    String getPreferredPosition();
    Integer getReliabilityScore();
    LocalDateTime getVerifiedAt();
    Long getCompletedMatches();
    Long getCountedMatches();
    BigDecimal getAverageRating();
    Long getRatingCount();
    LocalDateTime getAppliedAt();
    LocalDateTime getRespondedAt();
    LocalDateTime getWithdrawnAt();
}
