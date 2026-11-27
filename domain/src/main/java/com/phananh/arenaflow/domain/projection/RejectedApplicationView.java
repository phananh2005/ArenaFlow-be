package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đơn chờ vừa bị từ chối tự động. */
public interface RejectedApplicationView {
    UUID getId();
    UUID getRecruitmentId();
}
