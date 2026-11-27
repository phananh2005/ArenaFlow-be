package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đơn chờ còn sót trên tin đã đóng vừa bị từ chối — đủ dữ liệu cho event thông báo ứng viên. */
public interface OrphanRejectedApplicationView {
    UUID getId();
    UUID getRecruitmentId();
    UUID getApplicantId();
    UUID getMatchId();
    UUID getExternalMatchId();
    UUID getTeamId();
}
