package com.phananh.arenaflow.domain.entity.match;

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
 * Entity đại diện cho match (bài đăng ghép kèo)
 */
@Entity
@Table(name = "match")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Match {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private Type type;

    @Column(name = "author_team_id", nullable = false)
    private UUID authorTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "sport_id", nullable = false)
    private UUID sportId;

    @Column(name = "format", length = 10)
    private String format;

    @Column(name = "age_note", columnDefinition = "TEXT")
    private String ageNote;

    @Column(name = "match_nature", columnDefinition = "TEXT")
    private String matchNature;

    @Column(name = "venue_id")
    private UUID venueId;

    @Column(name = "match_start_time", nullable = false)
    private LocalDateTime matchStartTime;

    @Column(name = "match_end_time", nullable = false)
    private LocalDateTime matchEndTime;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "search_lat", precision = 10, scale = 7)
    private BigDecimal searchLat;

    @Column(name = "search_lng", precision = 10, scale = 7)
    private BigDecimal searchLng;

    @Column(name = "search_radius_km", precision = 5, scale = 2)
    private BigDecimal searchRadiusKm;

    @Column(name = "cost_type", length = 50)
    private String costType;

    @Column(name = "cost_split", length = 50)
    private String costSplit;

    @Column(name = "cost_venue_amount")
    private Long costVenueAmount;

    @Column(name = "cost_drinks_amount")
    private Long costDrinksAmount;

    @Column(name = "cost_deposit_holder", length = 100)
    private String costDepositHolder;

    @Column(name = "confirmed_opponent_team_id")
    private UUID confirmedOpponentTeamId;

    @Column(name = "confirmed_venue_id")
    private UUID confirmedVenueId;

    @Column(name = "confirmed_match_time")
    private LocalDateTime confirmedMatchTime;

    @Column(name = "cancelled_by_team_id")
    private UUID cancelledByTeamId;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

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

    public enum Type {
        HAS_VENUE, NEED_VENUE
    }

    public enum Status {
        OPEN, CONFIRMED, ARCHIVED, COMPLETED, EXPIRED, CANCELLED
    }
}
