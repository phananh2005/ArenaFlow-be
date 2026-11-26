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
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Report hành vi xấu: được chấp nhận ngay ({@code AUTO_ACCEPTED}). Trạng thái chỉ đổi bằng câu UPDATE có điều kiện
 * trên trạng thái nguồn (tranh chấp, quyết định Admin) nên {@code status} không ghi qua dirty checking. Số điểm thực
 * trừ ({@code reliabilityPenalty}) ghi một lần bằng dirty checking ngay trong transaction tạo report.
 */
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Report {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporter_type", nullable = false, length = 20, updatable = false)
    private ReporterType reporterType;

    @Column(name = "reporter_id", nullable = false, updatable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reported_type", nullable = false, length = 20, updatable = false)
    private ReportedType reportedType;

    @Column(name = "reported_id", nullable = false, updatable = false)
    private UUID reportedId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 50, updatable = false)
    private ReportType reportType;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    @Builder.Default
    private Status status = Status.AUTO_ACCEPTED;

    @Setter
    @Column(name = "reliability_penalty", nullable = false)
    @Builder.Default
    private Integer reliabilityPenalty = 0;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) và {@code updatedAt} = {@code createdAt} khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
        updatedAt = createdAt;
    }

    /** Cập nhật {@code updatedAt} = giờ hiện tại ({@link AppTime#now()}) mỗi lần entity được sửa. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = AppTime.now();
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
        AUTO_ACCEPTED, DISPUTED, CONFIRMED, REJECTED
    }
}
