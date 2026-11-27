package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import com.phananh.arenaflow.domain.entity.mercenary.MercenaryRecruitment;

import java.util.UUID;

/** Frame gửi tới topic của cuộc trò chuyện. */
public sealed interface RealtimeFrame {

    /** Đội trưởng duyệt / từ chối, ứng viên rút đơn. */
    record ApplicationUpdated(UUID applicationId, MercenaryApplication.Status status, UUID recruitmentId,
                              MercenaryRecruitment.Status recruitmentStatus, int quantityApproved)
            implements RealtimeFrame {
    }
}
