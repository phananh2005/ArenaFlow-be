package com.phananh.arenaflow.domain.entity.match;

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
 * Entity đại diện cho pairing (luồng B - nhiều đội thương lượng)
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

    @Column(name = "author_confirmed_at")
    private OffsetDateTime authorConfirmedAt;

    @Column(name = "interested_confirmed_at")
    private OffsetDateTime interestedConfirmedAt;

    @Column(name = "final_confirmed_at")
    private OffsetDateTime finalConfirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public enum Status {
        NEGOTIATING, CONFIRMED, EXPIRED, SUPERSEDED
    }
}
