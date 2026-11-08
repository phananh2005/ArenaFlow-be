package com.phananh.arenaflow.domain.entity.rating;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Entity đại diện cho rating (đánh giá sau trận)
 */
@Entity
@Table(name = "rating")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
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
    @Setter(AccessLevel.NONE)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public enum RaterType {
        TEAM, INDIVIDUAL
    }

    public enum RateeType {
        TEAM, INDIVIDUAL
    }
}
