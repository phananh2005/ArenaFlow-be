package com.phananh.arenaflow.domain.entity.rating;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho report (báo cáo hành vi xấu)
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
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
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public ReporterType getReporterType() {
        return reporterType;
    }

    public void setReporterType(ReporterType reporterType) {
        this.reporterType = reporterType;
    }

    public UUID getReporterId() {
        return reporterId;
    }

    public void setReporterId(UUID reporterId) {
        this.reporterId = reporterId;
    }

    public ReportedType getReportedType() {
        return reportedType;
    }

    public void setReportedType(ReportedType reportedType) {
        this.reportedType = reportedType;
    }

    public UUID getReportedId() {
        return reportedId;
    }

    public void setReportedId(UUID reportedId) {
        this.reportedId = reportedId;
    }

    public ReportType getReportType() {
        return reportType;
    }

    public void setReportType(ReportType reportType) {
        this.reportType = reportType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OffsetDateTime getAdminReviewedAt() {
        return adminReviewedAt;
    }

    public void setAdminReviewedAt(OffsetDateTime adminReviewedAt) {
        this.adminReviewedAt = adminReviewedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
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
