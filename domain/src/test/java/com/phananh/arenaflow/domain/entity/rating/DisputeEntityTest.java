package com.phananh.arenaflow.domain.entity.rating;

import com.phananh.arenaflow.common.time.AppTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.phananh.arenaflow.domain.entity.ColumnMapping.column;
import static org.assertj.core.api.Assertions.assertThat;

class DisputeEntityTest {

    @Test
    void builderDefaultsToPendingWithoutDecision() {
        UUID reportId = UUID.randomUUID();
        Dispute dispute = Dispute.builder().reportId(reportId).evidence("Có mặt đúng giờ")
                .createdByUserId(UUID.randomUUID()).build();

        assertThat(dispute.getStatus()).isEqualTo(Dispute.Status.PENDING);
        assertThat(dispute.getDecision()).isNull();
        assertThat(dispute.getReportId()).isEqualTo(reportId);
    }

    @Test
    void decisionColumnsAreChangedOnlyByConditionalUpdate() {
        for (String field : List.of("reportId", "status", "decision", "resolutionNote", "resolvedByUserId", "resolvedAt",
                "evidence", "createdByUserId")) {
            assertThat(column(Dispute.class, field).updatable()).as(field).isFalse();
        }
        assertThat(Dispute.Decision.values()).containsExactly(Dispute.Decision.CONFIRMED, Dispute.Decision.REJECTED);
    }

    @Test
    void callbacksSetCreatedAndUpdatedAt() {
        Dispute dispute = Dispute.builder().build();
        LocalDateTime before = AppTime.now().minusSeconds(1);

        dispute.onCreate();
        dispute.onUpdate();

        assertThat(dispute.getCreatedAt()).isAfter(before);
        assertThat(dispute.getUpdatedAt()).isAfterOrEqualTo(dispute.getCreatedAt());
    }
}
