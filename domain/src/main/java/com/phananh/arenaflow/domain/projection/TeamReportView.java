package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Report trong danh sách của một đội + giờ trận + đội còn lại + tranh chấp (cột dispute null khi chưa có). */
public interface TeamReportView {

    UUID getReportId();

    UUID getMatchId();

    LocalDateTime getMatchStartTime();

    UUID getCounterpartTeamId();

    String getCounterpartTeamName();

    String getCounterpartTeamAvatarUrl();

    String getReportType();

    String getReason();

    String getStatus();

    Integer getReliabilityPenalty();

    LocalDateTime getCreatedAt();

    UUID getDisputeId();

    String getDisputeStatus();

    String getDisputeEvidence();

    String getDisputeDecision();

    String getDisputeResolutionNote();

    LocalDateTime getDisputeCreatedAt();

    LocalDateTime getDisputeResolvedAt();
}
