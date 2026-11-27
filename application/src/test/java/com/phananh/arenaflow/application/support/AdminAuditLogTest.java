package com.phananh.arenaflow.application.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class AdminAuditLogTest {

    private final AdminAuditLog auditLog = new AdminAuditLog();
    private final UUID adminId = UUID.randomUUID();
    private final UUID venueId = UUID.randomUUID();

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void writesOnlyIdsExactlyOnceAfterCommit(CapturedOutput output) {
        TransactionSynchronizationManager.initSynchronization();

        auditLog.afterCommit(AdminAuditLog.ADMIN_DELETE_IMAGE, adminId, venueId, null);
        assertThat(output.getAll()).doesNotContain("Admin action");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(output.getAll()).containsOnlyOnce("Admin action: action=ADMIN_DELETE_IMAGE, adminId=" + adminId
                + ", venueId=" + venueId + ", reportId=null");
    }

    @Test
    void neutralOverloadWritesTargetTypeAndIdAfterCommit(CapturedOutput output) {
        UUID sportId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        auditLog.afterCommit(AdminAuditLog.SET_SPORT_ACTIVE, adminId, "SPORT", sportId);
        assertThat(output.getAll()).doesNotContain("SET_SPORT_ACTIVE");

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        assertThat(output.getAll()).containsOnlyOnce("Admin action: action=SET_SPORT_ACTIVE, adminId=" + adminId
                + ", targetType=SPORT, targetId=" + sportId);
    }

    @Test
    void writesNothingWhenTransactionRollsBack(CapturedOutput output) {
        TransactionSynchronizationManager.initSynchronization();

        auditLog.afterCommit(AdminAuditLog.ADMIN_DELETE_IMAGE, adminId, venueId, null);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        assertThat(output.getAll()).doesNotContain("Admin action");
    }

    @Test
    void rejectsCallOutsideTransaction() {
        assertThatThrownBy(() -> auditLog.afterCommit(AdminAuditLog.ADMIN_DELETE_IMAGE, adminId, venueId, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
