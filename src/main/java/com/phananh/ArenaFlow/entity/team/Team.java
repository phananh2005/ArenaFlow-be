package com.phananh.ArenaFlow.entity.team;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho team (đội)
 */
@Entity
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TeamStatus status = TeamStatus.ACTIVE;

    @Column(name = "reliability_score", nullable = false)
    private Integer reliabilityScore = 80;

    @Column(name = "total_matches_completed", nullable = false)
    private Integer totalMatchesCompleted = 0;

    @Column(name = "total_matches_confirmed", nullable = false)
    private Integer totalMatchesConfirmed = 0;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "report_count_noshow", nullable = false)
    private Integer reportCountNoshow = 0;

    @Column(name = "report_count_bad_play", nullable = false)
    private Integer reportCountBadPlay = 0;

    @Column(name = "report_count_bad_communication", nullable = false)
    private Integer reportCountBadCommunication = 0;

    @Column(name = "report_count_skill_mismatch", nullable = false)
    private Integer reportCountSkillMismatch = 0;

    @Column(name = "clean_streak", nullable = false)
    private Integer cleanStreak = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(UUID createdBy) {
        this.createdBy = createdBy;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TeamStatus getStatus() {
        return status;
    }

    public void setStatus(TeamStatus status) {
        this.status = status;
    }

    public Integer getReliabilityScore() {
        return reliabilityScore;
    }

    public void setReliabilityScore(Integer reliabilityScore) {
        this.reliabilityScore = reliabilityScore;
    }

    public Integer getTotalMatchesCompleted() {
        return totalMatchesCompleted;
    }

    public void setTotalMatchesCompleted(Integer totalMatchesCompleted) {
        this.totalMatchesCompleted = totalMatchesCompleted;
    }

    public Integer getTotalMatchesConfirmed() {
        return totalMatchesConfirmed;
    }

    public void setTotalMatchesConfirmed(Integer totalMatchesConfirmed) {
        this.totalMatchesConfirmed = totalMatchesConfirmed;
    }

    public BigDecimal getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(BigDecimal averageRating) {
        this.averageRating = averageRating;
    }

    public Integer getReportCountNoshow() {
        return reportCountNoshow;
    }

    public void setReportCountNoshow(Integer reportCountNoshow) {
        this.reportCountNoshow = reportCountNoshow;
    }

    public Integer getReportCountBadPlay() {
        return reportCountBadPlay;
    }

    public void setReportCountBadPlay(Integer reportCountBadPlay) {
        this.reportCountBadPlay = reportCountBadPlay;
    }

    public Integer getReportCountBadCommunication() {
        return reportCountBadCommunication;
    }

    public void setReportCountBadCommunication(Integer reportCountBadCommunication) {
        this.reportCountBadCommunication = reportCountBadCommunication;
    }

    public Integer getReportCountSkillMismatch() {
        return reportCountSkillMismatch;
    }

    public void setReportCountSkillMismatch(Integer reportCountSkillMismatch) {
        this.reportCountSkillMismatch = reportCountSkillMismatch;
    }

    public Integer getCleanStreak() {
        return cleanStreak;
    }

    public void setCleanStreak(Integer cleanStreak) {
        this.cleanStreak = cleanStreak;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum TeamStatus {
        ACTIVE, INACTIVE
    }
}
