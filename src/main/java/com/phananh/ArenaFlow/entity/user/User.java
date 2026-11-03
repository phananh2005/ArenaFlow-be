package com.phananh.ArenaFlow.entity.user;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho user (tài khoản cá nhân)
 * Mapping từ bảng "user" trong database
 */
@Entity
@Table(name = "\"user\"") // "user" là từ khoá PostgreSQL → phải quote, khớp V1 (CREATE TABLE "user")
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "preferred_position", length = 50)
    private String preferredPosition;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(name = "ward_id")
    private UUID wardId;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

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

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getPreferredPosition() {
        return preferredPosition;
    }

    public void setPreferredPosition(String preferredPosition) {
        this.preferredPosition = preferredPosition;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UUID getWardId() {
        return wardId;
    }

    public void setWardId(UUID wardId) {
        this.wardId = wardId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
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

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(OffsetDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public enum Role {
        USER, ADMIN
    }

    // Soft delete helper
    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
