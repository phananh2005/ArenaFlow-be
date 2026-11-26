package com.phananh.arenaflow.domain.entity.rating;

import com.phananh.arenaflow.common.time.AppTime;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static com.phananh.arenaflow.domain.entity.ColumnMapping.column;
import static com.phananh.arenaflow.domain.entity.ColumnMapping.hasField;
import static org.assertj.core.api.Assertions.assertThat;

class ReportEntityTest {

    @Test
    void builderDefaultsToAutoAcceptedWithZeroPenalty() {
        Report report = Report.builder().reportType(Report.ReportType.NOSHOW).reason("Không đến").build();

        assertThat(report.getStatus()).isEqualTo(Report.Status.AUTO_ACCEPTED);
        assertThat(report.getReliabilityPenalty()).isZero();
    }

    @Test
    void statusValuesFollowDisputeLifecycle() {
        assertThat(Report.Status.values()).containsExactly(Report.Status.AUTO_ACCEPTED, Report.Status.DISPUTED,
                Report.Status.CONFIRMED, Report.Status.REJECTED);
    }

    @Test
    void penaltyIsWrittenByDirtyCheckingButStatusIsNot() {
        Report report = Report.builder().build();

        report.setReliabilityPenalty(8);

        assertThat(report.getReliabilityPenalty()).isEqualTo(8);
        assertThat(column(Report.class, "reliabilityPenalty").updatable()).isTrue();
        assertThat(column(Report.class, "status").updatable()).isFalse();
        assertThat(column(Report.class, "reason").nullable()).isFalse();
        assertThat(column(Report.class, "createdByUserId").nullable()).isFalse();
        assertThat(hasField(Report.class, "adminReviewedAt")).isFalse();
    }

    @Test
    void callbacksSetCreatedAndUpdatedAt() {
        Report report = Report.builder().build();
        LocalDateTime before = AppTime.now().minusSeconds(1);

        report.onCreate();
        LocalDateTime created = report.getCreatedAt();
        report.onUpdate();

        assertThat(created).isAfter(before);
        assertThat(report.getUpdatedAt()).isAfterOrEqualTo(created);
    }
}
