package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đơn vừa rút do xoá tài khoản ứng viên; {@code wasApproved} = trạng thái trước là APPROVED. */
public interface WithdrawnApplicationView {
    UUID getId();
    UUID getRecruitmentId();
    UUID getTeamId();
    UUID getMatchId();
    UUID getExternalMatchId();
    Boolean getWasApproved();
}
