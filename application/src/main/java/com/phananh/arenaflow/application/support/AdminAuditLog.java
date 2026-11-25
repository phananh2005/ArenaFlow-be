package com.phananh.arenaflow.application.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Ghi log thao tác Admin chỉ khi transaction bao ngoài đã commit; chỉ ghi id, không PII. */
@Slf4j
@Component
public class AdminAuditLog {

    public static final String MARK_INACTIVE = "MARK_INACTIVE";
    public static final String REACTIVATE = "REACTIVATE";
    public static final String RESOLVE_REPORT = "RESOLVE_REPORT";
    public static final String ADMIN_UPDATE_VENUE = "ADMIN_UPDATE_VENUE";
    public static final String ADMIN_DELETE_IMAGE = "ADMIN_DELETE_IMAGE";
    public static final String SET_SPORT_ACTIVE = "SET_SPORT_ACTIVE";

    public void afterCommit(String action, UUID adminId, UUID venueId, UUID reportId) {
        registerAfterCommit(() -> log.info("Admin action: action={}, adminId={}, venueId={}, reportId={}",
                action, adminId, venueId, reportId));
    }

    /** Bản trung tính cho đối tượng ngoài địa điểm (vd {@code targetType = "SPORT"}). */
    public void afterCommit(String action, UUID adminId, String targetType, UUID targetId) {
        registerAfterCommit(() -> log.info("Admin action: action={}, adminId={}, targetType={}, targetId={}",
                action, adminId, targetType, targetId));
    }

    private static void registerAfterCommit(Runnable writer) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("AdminAuditLog requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                writer.run();
            }
        });
    }
}
