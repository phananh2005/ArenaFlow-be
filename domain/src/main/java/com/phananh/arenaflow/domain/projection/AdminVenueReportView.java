package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Report kèm tên địa điểm, người report và người xử lý (không che) cùng các cờ đã xoá, từ một native query.
 * Cột thời điểm ({@code handledAt}, {@code createdAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code
 * LocalDateTime}.
 */
public interface AdminVenueReportView {
    UUID getReportId();
    UUID getVenueId();
    String getVenueName();
    String getVenueStatus();
    UUID getReporterId();
    String getReporterName();
    Boolean getReporterDeleted();
    String getReason();
    String getDetail();
    String getStatus();
    UUID getHandledById();
    String getHandledByName();
    Boolean getHandledByDeleted();
    LocalDateTime getHandledAt();
    LocalDateTime getCreatedAt();
}
