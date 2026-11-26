package com.phananh.arenaflow.domain.entity.rating;

import com.phananh.arenaflow.common.time.AppTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Đánh giá sau trận. Không sửa, không xoá sau khi gửi → mọi cột {@code updatable = false}.
 * "Đội kia không đến" ({@code noShow = true}) không có sao và không có tỷ số.
 */
@Entity
@Table(name = "rating")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Rating {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "match_id", nullable = false, updatable = false)
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rater_type", nullable = false, length = 20, updatable = false)
    private RaterType raterType;

    @Column(name = "rater_id", nullable = false, updatable = false)
    private UUID raterId;

    @Enumerated(EnumType.STRING)
    @Column(name = "ratee_type", nullable = false, length = 20, updatable = false)
    private RateeType rateeType;

    @Column(name = "ratee_id", nullable = false, updatable = false)
    private UUID rateeId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "score", updatable = false)
    private Integer score;

    @Column(name = "is_no_show", nullable = false, updatable = false)
    @Builder.Default
    private boolean noShow = false;

    @Column(name = "comment", columnDefinition = "TEXT", updatable = false)
    private String comment;

    @Column(name = "rater_team_score", updatable = false)
    private Integer raterTeamScore;

    @Column(name = "ratee_team_score", updatable = false)
    private Integer rateeTeamScore;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }

    public enum RaterType {
        TEAM, INDIVIDUAL
    }

    public enum RateeType {
        TEAM, INDIVIDUAL
    }
}
