package com.phananh.arenaflow.domain.entity.team;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho team_captain_history (lịch sử chuyển quyền đội trưởng)
 */
@Entity
@Table(name = "team_captain_history")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TeamCaptainHistory {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "from_user_id")
    private UUID fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private UUID toUserId;

    @Column(name = "transferred_at", nullable = false)
    private OffsetDateTime transferredAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @PrePersist
    protected void onCreate() {
        if (transferredAt == null) {
            transferredAt = OffsetDateTime.now();
        }
    }
}
