package com.phananh.arenaflow.domain.entity.match;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho pairing (luồng B - nhiều đội thương lượng)
 */
@Entity
@Table(name = "pairing")
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

    public UUID getInterestedTeamId() {
        return interestedTeamId;
    }

    public void setInterestedTeamId(UUID interestedTeamId) {
        this.interestedTeamId = interestedTeamId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OffsetDateTime getAuthorConfirmedAt() {
        return authorConfirmedAt;
    }

    public void setAuthorConfirmedAt(OffsetDateTime authorConfirmedAt) {
        this.authorConfirmedAt = authorConfirmedAt;
    }

    public OffsetDateTime getInterestedConfirmedAt() {
        return interestedConfirmedAt;
    }

    public void setInterestedConfirmedAt(OffsetDateTime interestedConfirmedAt) {
        this.interestedConfirmedAt = interestedConfirmedAt;
    }

    public OffsetDateTime getFinalConfirmedAt() {
        return finalConfirmedAt;
    }

    public void setFinalConfirmedAt(OffsetDateTime finalConfirmedAt) {
        this.finalConfirmedAt = finalConfirmedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public enum Status {
        NEGOTIATING, CONFIRMED, EXPIRED, SUPERSEDED
    }
}
