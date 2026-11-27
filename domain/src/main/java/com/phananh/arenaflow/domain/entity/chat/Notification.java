package com.phananh.arenaflow.domain.entity.chat;

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
 * Thông báo in-app. Chỉ dùng để kiểm schema và đọc lẻ: mọi INSERT đi qua câu ghi gom lô của repository
 * (id do {@code uuidv7()} sinh, {@code created_at} truyền từ {@code Clock}), mọi UPDATE qua câu {@code @Modifying}.
 */
@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private Type type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 50)
    private ReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }

    /** Loại thông báo — khớp CHECK {@code notification_type_check}. */
    public enum Type {
        WELCOME, TEAM_INVITATION, TEAM_INVITATION_ACCEPTED, TEAM_INVITATION_REJECTED,
        CAPTAIN_TRANSFERRED, TEAM_MARKED_INACTIVE, TEAM_REACTIVATED,
        MATCH_REQUEST_RECEIVED, REQUEST_WITHDRAWN, REQUEST_ACCEPTED, REQUEST_REJECTED, REQUEST_SUPERSEDED, REQUEST_EXPIRED,
        PAIRING_NEW, PAIRING_SUPERSEDED, PAIRING_EXPIRED,
        MATCH_CONFIRMED, MATCH_CANCELLED, MATCH_EXPIRED, REMINDER_BEFORE_MATCH, MATCH_COMPLETED, MERCENARY_RATING_REMINDER,
        RATING_RECEIVED, REPORT_RECEIVED, REPORT_DISPUTED, DISPUTE_RESOLVED, DISPUTE_CREATED,
        VERIFICATION_BADGE_GRANTED, VERIFICATION_BADGE_REVOKED,
        MERCENARY_APPLICATION, MERCENARY_APPLICATION_APPROVED, MERCENARY_APPLICATION_REJECTED,
        MERCENARY_WITHDRAWN, MERCENARY_GAME_CANCELLED,
        VENUE_ADMIN_ALERT
    }

    /** Màn hình liên quan mà {@code referenceId} trỏ tới — khớp CHECK {@code notification_reference_type_check}. */
    public enum ReferenceType {
        MATCH, PAIRING, TEAM, TEAM_INVITATION, USER, VENUE, REPORT, DISPUTE, MERCENARY_DISPUTE, MERCENARY_APPLICATION
    }
}
