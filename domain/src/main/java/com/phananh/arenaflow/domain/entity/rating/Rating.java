package com.phananh.arenaflow.domain.entity.rating;

import jakarta.persistence.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho rating (đánh giá sau trận)
 */
@Entity
@Table(name = "rating")
public class Rating {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rater_type", nullable = false, length = 20)
    private RaterType raterType;

    @Column(name = "rater_id", nullable = false)
    private UUID raterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ratee_type", nullable = false, length = 20)
    private RateeType rateeType;

    @Column(name = "ratee_id", nullable = false)
    private UUID rateeId;

    @Column(name = "score", nullable = false, precision = 2, scale = 1)
    private BigDecimal score;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "match_score_home")
    private Integer matchScoreHome;

    @Column(name = "match_score_away")
    private Integer matchScoreAway;

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

    public RaterType getRaterType() {
        return raterType;
    }

    public void setRaterType(RaterType raterType) {
        this.raterType = raterType;
    }

    public UUID getRaterId() {
        return raterId;
    }

    public void setRaterId(UUID raterId) {
        this.raterId = raterId;
    }

    public RateeType getRateeType() {
        return rateeType;
    }

    public void setRateeType(RateeType rateeType) {
        this.rateeType = rateeType;
    }

    public UUID getRateeId() {
        return rateeId;
    }

    public void setRateeId(UUID rateeId) {
        this.rateeId = rateeId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getMatchScoreHome() {
        return matchScoreHome;
    }

    public void setMatchScoreHome(Integer matchScoreHome) {
        this.matchScoreHome = matchScoreHome;
    }

    public Integer getMatchScoreAway() {
        return matchScoreAway;
    }

    public void setMatchScoreAway(Integer matchScoreAway) {
        this.matchScoreAway = matchScoreAway;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public enum RaterType {
        TEAM, INDIVIDUAL
    }

    public enum RateeType {
        TEAM, INDIVIDUAL
    }
}
