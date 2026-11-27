package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển kèm đội tuyển, đội trưởng hiện tại, trận và cờ đã đánh giá của hai bên. */
public interface ApplicationAudienceRowView {
    UUID getApplicationId();
    String getApplicationStatus();
    UUID getApplicantId();
    String getPosition();
    UUID getRecruitmentId();
    UUID getTeamId();
    String getTeamName();
    UUID getCaptainUserId();
    UUID getMatchId();
    UUID getExternalMatchId();
    LocalDateTime getGameStartTime();
    String getVenueName();
    Boolean getRatedByTeam();
    Boolean getRatedByApplicant();
}
