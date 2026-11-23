package com.phananh.arenaflow.domain.entity.venue;

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
 * Entity đại diện cho venue_report (báo cáo địa điểm ngừng hoạt động)
 */
@Entity
@Table(name = "venue_report")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class VenueReport {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private UUID venueId;

    @Column(name = "reporter_id", nullable = false)
    private UUID reporterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private Reason reason;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }

    public enum Reason {
        CLOSED, WRONG_INFO, OTHER
    }

    public enum Status {
        PENDING, VERIFIED, REJECTED
    }
}
