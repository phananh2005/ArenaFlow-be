package com.phananh.arenaflow.application.event.mercenary;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;
import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển bị rút (ứng viên rút hoặc xoá tài khoản). Bản tạm của NOTIFICATION-001 — bản của mã sở hữu thay thế khi tích hợp. */
public record MercenaryApplicationWithdrawnEvent(UUID applicationId, UUID recruitmentId, UUID matchId, UUID externalMatchId, UUID teamId,
        UUID applicantId, MercenaryApplication.Status previousStatus, Cause cause, LocalDateTime occurredAt) {

    public enum Cause {
        APPLICANT, ACCOUNT_DELETED
    }
}
