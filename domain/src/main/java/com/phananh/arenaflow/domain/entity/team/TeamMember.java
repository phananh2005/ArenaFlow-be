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
 * Entity đại diện cho team_member (quan hệ user-team).
 * {@code version}: optimistic lock — ghi qua entity lệch version bị từ chối lúc flush/commit và được retry ở tầng
 * service. Bulk UPDATE native không được kiểm version: phải tự tăng {@code version} để các ghi song song bị từ chối.
 */
@Entity
@Table(name = "team_member")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class TeamMember {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "invited_at", nullable = false)
    private OffsetDateTime invitedAt;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;

    @Version
    @Column(name = "version", nullable = false)
    @Setter(AccessLevel.NONE)
    private Long version;

    @PrePersist
    protected void onCreate() {
        if (invitedAt == null) {
            invitedAt = OffsetDateTime.now();
        }
    }

    public enum Role {
        CAPTAIN, MEMBER
    }

    public enum Status {
        PENDING, ACTIVE, LEFT, REJECTED
    }
}
