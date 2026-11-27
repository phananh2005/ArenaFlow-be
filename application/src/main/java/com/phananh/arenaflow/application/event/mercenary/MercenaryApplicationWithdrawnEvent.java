package com.phananh.arenaflow.application.event.mercenary;

import com.phananh.arenaflow.domain.entity.mercenary.MercenaryApplication;

import java.time.LocalDateTime;
import java.util.UUID;

/** Đơn ứng tuyển bị rút — bởi ứng viên hoặc do ứng viên xoá tài khoản. */
public record MercenaryApplicationWithdrawnEvent(UUID applicationId, UUID recruitmentId, UUID matchId,
                                                 UUID externalMatchId, UUID teamId, UUID applicantId,
                                                 MercenaryApplication.Status previousStatus, Cause cause,
                                                 LocalDateTime occurredAt) {

    public enum Cause {
        APPLICANT, ACCOUNT_DELETED
    }
}
