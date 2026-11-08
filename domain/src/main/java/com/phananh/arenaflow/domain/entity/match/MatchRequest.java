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
 * Entity đại diện cho match_request (yêu cầu bắt đối - luồng A)
 */
@Entity
@Table(name = "match_request")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class MatchRequest {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "requester_team_id", nullable = false)
    private UUID requesterTeamId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;

    @Column(name = "withdrawn_at")
    private OffsetDateTime withdrawnAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) {
            requestedAt = OffsetDateTime.now();
        }
    }

    public enum Status {
        PENDING, ACCEPTED, REJECTED, WITHDRAWN, SUPERSEDED, EXPIRED
    }
}
