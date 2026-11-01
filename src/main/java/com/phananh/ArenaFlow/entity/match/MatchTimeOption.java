package com.phananh.ArenaFlow.entity.match;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho match_time_option (lựa chọn giờ hẹn cho luồng B)
 */
@Entity
@Table(name = "match_time_option")
public class MatchTimeOption {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "proposed_start_time", nullable = false)
    private OffsetDateTime proposedStartTime;

    @Column(name = "proposed_end_time", nullable = false)
    private OffsetDateTime proposedEndTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getMatchId() {
        return matchId;
    }

    public void setMatchId(UUID matchId) {
        this.matchId = matchId;
    }

    public OffsetDateTime getProposedStartTime() {
        return proposedStartTime;
    }

    public void setProposedStartTime(OffsetDateTime proposedStartTime) {
        this.proposedStartTime = proposedStartTime;
    }

    public OffsetDateTime getProposedEndTime() {
        return proposedEndTime;
    }

    public void setProposedEndTime(OffsetDateTime proposedEndTime) {
        this.proposedEndTime = proposedEndTime;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
