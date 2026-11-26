package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;

/** Chi tiết tranh chấp cho Admin: thêm trận, lý do report, bằng chứng, quyết định, điểm hiện tại của hai đội. */
public interface AdminDisputeDetailView extends AdminDisputeView {

    String getSportName();

    String getMatchStatus();

    LocalDateTime getMatchEndTime();

    String getVenueName();

    String getReportReason();

    Integer getReliabilityPenalty();

    String getEvidence();

    String getResolutionNote();

    Integer getReporterTeamReliabilityScore();

    Integer getReportedTeamReliabilityScore();
}
