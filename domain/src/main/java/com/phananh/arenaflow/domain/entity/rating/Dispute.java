package com.phananh.arenaflow.domain.entity.rating;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tranh chấp 1:1 với một report bùng. Chỉ INSERT qua entity; quyết định của Admin ghi bằng câu UPDATE có điều kiện
 * {@code status = 'PENDING'} nên các cột quyết định không ghi qua dirty checking.
 */
@Entity
@Table(name = "dispute")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Dispute {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "report_id", nullable = false, updatable = false)
    private UUID reportId;

    @Column(name = "evidence", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String evidence;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", length = 20, updatable = false)
    private Decision decision;

    @Column(name = "resolution_note", length = 500, updatable = false)
    private String resolutionNote;

    @Column(name = "resolved_by_user_id", updatable = false)
    private UUID resolvedByUserId;

    @Column(name = "resolved_at", updatable = false)
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = AppTime.now();
    }

    public enum Status {
        PENDING, RESOLVED
    }

    public enum Decision {
        CONFIRMED, REJECTED
    }
}
