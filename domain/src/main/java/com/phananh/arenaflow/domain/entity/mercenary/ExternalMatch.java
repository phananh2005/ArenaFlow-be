package com.phananh.arenaflow.domain.entity.mercenary;

import com.phananh.arenaflow.common.time.AppTime;
import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Trận ngoài hệ thống do đội trưởng khai báo để tuyển mercenary. Chỉ INSERT + đọc qua JPA: sửa / huỷ là câu UPDATE
 * native có điều kiện {@code version} / trạng thái, tự tăng {@code version} (khoá trận ngoài trước khi khoá tin).
 * Kết thúc tính theo {@code matchEndTime}, không có cột trạng thái riêng.
 */
@Entity
@Table(name = "external_match")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExternalMatch {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "team_id", nullable = false, updatable = false)
    private UUID teamId;

    @Column(name = "venue_id", nullable = false, updatable = false)
    private UUID venueId;

    @Column(name = "match_start_time", nullable = false, updatable = false)
    private LocalDateTime matchStartTime;

    @Column(name = "match_end_time", nullable = false, updatable = false)
    private LocalDateTime matchEndTime;

    @Convert(converter = SkillLevelsConverter.class)
    @Column(name = "skill_levels", nullable = false, columnDefinition = "TEXT", updatable = false)
    @Builder.Default
    private List<String> skillLevels = List.of();

    @Column(name = "opponent_name", length = 100, updatable = false)
    private String opponentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    @Builder.Default
    private Status status = Status.SCHEDULED;

    @Column(name = "cancelled_at", updatable = false)
    private LocalDateTime cancelledAt;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
        updatedAt = createdAt;
    }

    public enum Status {
        SCHEDULED, CANCELLED
    }
}
