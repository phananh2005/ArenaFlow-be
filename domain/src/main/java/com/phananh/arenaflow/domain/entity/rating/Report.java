package com.phananh.arenaflow.domain.entity.rating;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho report (báo cáo hành vi xấu)
 */
@Entity
@Table(name = "report")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Report {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporter_type", nullable = false, length = 20)
    private ReporterType reporterType;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_type", nullable = false, length = 20)
    private ReportedType reportedType;

    @Column(name = "reported_id", nullable = false)
    private UUID reportedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50)
    private ReportType reportType;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "admin_reviewed_at")
    private OffsetDateTime adminReviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public enum ReporterType {
        TEAM, INDIVIDUAL
    }

    public enum ReportedType {
        TEAM, INDIVIDUAL
    }

    public enum ReportType {
        NOSHOW, BAD_PLAY, BAD_COMMUNICATION, SKILL_MISMATCH
    }

    public enum Status {
        PENDING, CONFIRMED, REJECTED
    }
}
