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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho venue (địa điểm sân)
 */
@Entity
@Table(name = "venue")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Venue {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_type", nullable = false, length = 20)
    private CreatedByType createdByType;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "website", length = 500)
    private String website;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", length = 50)
    private VenueType venueType;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "stale_alerted_at")
    private LocalDateTime staleAlertedAt;

    @Column(name = "closure_alerted_at")
    private LocalDateTime closureAlertedAt;

    @Column(name = "ward_id")
    private UUID wardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "last_selected_at")
    private LocalDateTime lastSelectedAt;

    @Column(name = "admin_verified_inactive_at")
    private LocalDateTime adminVerifiedInactiveAt;

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

    public enum CreatedByType {
        ADMIN, USER
    }

    public enum Status {
        ACTIVE, INACTIVE
    }

    public enum VenueType {
        FIVE_A_SIDE, SEVEN_A_SIDE, ELEVEN_A_SIDE
    }
}
