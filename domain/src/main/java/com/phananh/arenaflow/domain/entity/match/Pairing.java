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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thương lượng ghép kèo luồng B giữa đội tạo kèo và một đội nhận kèo, mang gói thương lượng (phương án + chi phí
 * + cọc). Không có {@code @Version}: entity chỉ INSERT qua JPA; mọi đổi trạng thái là câu {@code UPDATE … WHERE}
 * tự viết với điều kiện {@code status} + {@code revision} (CAS) trong repository.
 */
@Entity
@Table(name = "pairing")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Pairing {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "interested_team_id", nullable = false)
    private UUID interestedTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    /** Phiên bản gói thương lượng; tăng mỗi lần chốt phương án hoặc lưu chi phí. */
    @Builder.Default
    @Column(name = "revision", nullable = false)
    private int revision = 0;

    @Column(name = "proposed_venue_id")
    private UUID proposedVenueId;

    @Column(name = "proposed_time_option_id")
    private UUID proposedTimeOptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_type", length = 50)
    private Match.CostType costType;

    @Enumerated(EnumType.STRING)
    @Column(name = "cost_split", length = 50)
    private Match.CostSplit costSplit;

    @Column(name = "cost_venue_amount")
    private Long costVenueAmount;

    @Column(name = "cost_drinks_amount")
    private Long costDrinksAmount;

    @Column(name = "cost_deposit_amount")
    private Long costDepositAmount;

    @Column(name = "cost_deposit_holder", length = 100)
    private String costDepositHolder;

    @Column(name = "author_confirmed_at")
    private LocalDateTime authorConfirmedAt;

    @Column(name = "interested_confirmed_at")
    private LocalDateTime interestedConfirmedAt;

    @Column(name = "final_confirmed_at")
    private LocalDateTime finalConfirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime updatedAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
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
        NEGOTIATING, CONFIRMED, EXPIRED, SUPERSEDED
    }
}
