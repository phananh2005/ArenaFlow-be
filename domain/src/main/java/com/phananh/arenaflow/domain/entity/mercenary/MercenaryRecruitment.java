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
 * Entity đại diện cho mercenary_recruitment (tin tuyển mercenary)
 */
@Entity
@Table(name = "mercenary_recruitment")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MercenaryRecruitment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position", nullable = false, length = 50)
    private Position position;

    @Column(name = "quantity_needed", nullable = false)
    private Integer quantityNeeded;

    @Column(name = "quantity_approved", nullable = false)
    @Builder.Default
    private Integer quantityApproved = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    /** Gán {@code createdAt} và {@code updatedAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
        updatedAt = AppTime.now();
    }

    /** Cập nhật {@code updatedAt} = giờ hiện tại ({@link AppTime#now()}) mỗi lần entity được sửa. */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = AppTime.now();
    }

    public enum Position {
        Thủ_môn, Hậu_vệ, Tiền_vệ, Tiền_đạo
    }

    public enum Status {
        OPEN, FILLED, EXPIRED, CLOSED
    }
}
