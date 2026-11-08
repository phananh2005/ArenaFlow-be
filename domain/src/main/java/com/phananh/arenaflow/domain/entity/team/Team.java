package com.phananh.arenaflow.domain.entity.team;

import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entity đại diện cho team (đội).
 * {@code version}: optimistic lock — ghi đồng thời lệch version bị từ chối lúc flush/commit và được retry ở tầng
 * service.
 */
@Entity
@Table(name = "team")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Team {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Convert(converter = SkillLevelsConverter.class)
    @Column(name = "skill_levels", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private List<String> skillLevels = List.of();

    @Column(name = "inactive_reason", length = 500)
    private String inactiveReason;

    @Column(name = "disband_reason", length = 100)
    private String disbandReason;

    @Column(name = "disbanded_at")
    private OffsetDateTime disbandedAt;

    @Column(name = "disbanded_by")
    private UUID disbandedBy;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TeamStatus status = TeamStatus.ACTIVE;

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
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
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

    public enum TeamStatus {
        ACTIVE, INACTIVE, DISBANDED
    }
}
