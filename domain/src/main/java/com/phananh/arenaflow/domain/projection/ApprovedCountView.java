package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Số đơn APPROVED vừa bớt khỏi một tin. */
public interface ApprovedCountView {
    UUID getRecruitmentId();
    Long getN();
}
