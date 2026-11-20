package com.phananh.arenaflow.domain.projection;

import java.time.Instant;
import java.util.UUID;

/** Report kèm tên địa điểm, người report và người xử lý (không che) cùng các cờ đã xoá, từ một native query. */
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
    Instant getHandledAt();
    Instant getCreatedAt();
}
