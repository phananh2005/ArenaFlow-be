package com.phananh.arenaflow.domain.entity.mercenary;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.persistence.*;
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
 * Entity đại diện cho mercenary_application (đơn ứng tuyển mercenary)
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

    @Column(name = "recruitment_id", nullable = false)
    private UUID recruitmentId;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "withdrawn_at")
    private LocalDateTime withdrawnAt;

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
