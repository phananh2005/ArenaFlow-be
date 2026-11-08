package com.phananh.arenaflow.domain.entity.user;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class User {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
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
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "ward_id")
    private UUID wardId;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "reliability_score", nullable = false)
    @Builder.Default
    private Integer reliabilityScore = 80;

    @Column(name = "total_matches_completed", nullable = false)
    @Builder.Default
    private Integer totalMatchesCompleted = 0;

    @Column(name = "total_matches_confirmed", nullable = false)
    @Builder.Default
    private Integer totalMatchesConfirmed = 0;

    @Column(name = "average_rating", precision = 3, scale = 2)
    private BigDecimal averageRating;

    @Column(name = "report_count_noshow", nullable = false)
    @Builder.Default
    private Integer reportCountNoshow = 0;

    @Column(name = "report_count_bad_play", nullable = false)
    @Builder.Default
    private Integer reportCountBadPlay = 0;

    @Column(name = "report_count_bad_communication", nullable = false)
    @Builder.Default
    private Integer reportCountBadCommunication = 0;

    @Column(name = "report_count_skill_mismatch", nullable = false)
    @Builder.Default
    private Integer reportCountSkillMismatch = 0;

    @Column(name = "clean_streak", nullable = false)
    @Builder.Default
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

    public enum Role {
        USER, ADMIN
    }
}
