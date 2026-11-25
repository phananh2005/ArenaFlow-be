package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Cặp (lựa chọn địa điểm, khung giờ) của chính kèo được chọn làm phương án, kèm thông tin địa điểm mới nhất. */
public interface ProposalCandidateView {
    UUID getVenueId();
    String getVenueName();
    String getAddress();
    String getVenueStatus();
    UUID getTimeOptionId();
    LocalDateTime getStartsAt();
    LocalDateTime getEndsAt();
}
