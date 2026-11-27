package com.phananh.arenaflow.domain.entity.mercenary;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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
 * Đơn ứng tuyển mercenary. Chỉ INSERT + đọc: trạng thái, thời điểm duyệt / rút chỉ đổi bằng câu UPDATE có điều kiện;
 * {@code gameCancelNotifiedAt} chỉ ghi bởi câu đánh dấu đã báo huỷ trận.
 */
@Entity
@Table(name = "mercenary_application")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MercenaryApplication {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "recruitment_id", nullable = false, updatable = false)
    private UUID recruitmentId;

    @Column(name = "applicant_id", nullable = false, updatable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20, updatable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "responded_at", updatable = false)
    private LocalDateTime respondedAt;

    @Column(name = "withdrawn_at", updatable = false)
    private LocalDateTime withdrawnAt;

    @Column(name = "game_cancel_notified_at", insertable = false, updatable = false)
    private LocalDateTime gameCancelNotifiedAt;

    /** Gán {@code appliedAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu, chỉ khi service chưa gán sẵn. */
    @PrePersist
    protected void onCreate() {
        if (appliedAt == null) {
            appliedAt = AppTime.now();
        }
    }

    public enum Status {
        PENDING, APPROVED, REJECTED, WITHDRAWN
    }
}
