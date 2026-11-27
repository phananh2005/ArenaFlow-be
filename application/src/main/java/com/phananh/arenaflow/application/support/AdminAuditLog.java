package com.phananh.arenaflow.application.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/** Ghi log thao tác Admin trên dữ liệu VENUE chỉ khi transaction bao ngoài đã commit; chỉ ghi id, không PII. */
@Slf4j
@Component
public class AdminAuditLog {

    public static final String MARK_INACTIVE = "MARK_INACTIVE";
    public static final String REACTIVATE = "REACTIVATE";
    public static final String RESOLVE_REPORT = "RESOLVE_REPORT";
    public static final String ADMIN_UPDATE_VENUE = "ADMIN_UPDATE_VENUE";
    public static final String ADMIN_DELETE_IMAGE = "ADMIN_DELETE_IMAGE";

    public void afterCommit(String action, UUID adminId, UUID venueId, UUID reportId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("AdminAuditLog requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Admin action: action={}, adminId={}, venueId={}, reportId={}", action, adminId, venueId, reportId);
            }
        });
    }

    /** Biến thể trung tính cho mọi loại đối tượng; cùng cơ chế ghi sau commit. */
    public void afterCommit(String action, UUID adminId, String targetType, UUID targetId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("AdminAuditLog requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info("Admin action: action={}, adminId={}, targetType={}, targetId={}", action, adminId, targetType,
                        targetId);
            }
        });
    }
}
