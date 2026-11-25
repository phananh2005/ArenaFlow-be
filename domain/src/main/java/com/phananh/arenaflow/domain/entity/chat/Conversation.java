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
 * Cuộc trò chuyện dùng chung: loại ngữ cảnh + trận + hai bên (mỗi bên là đội hoặc người dùng). Chỉ INSERT qua builder
 * và đọc.
 */
@Entity
@Table(name = "conversation")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Conversation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 30)
    private ContextType contextType;

    @Column(name = "match_id")
    private UUID matchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_a_type", nullable = false, length = 10)
    private PartyType partyAType;

    @Column(name = "party_a_id", nullable = false)
    private UUID partyAId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_b_type", nullable = false, length = 10)
    private PartyType partyBType;

    @Column(name = "party_b_id", nullable = false)
    private UUID partyBId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }

    public enum ContextType {
        TEAM_MATCH
    }

    public enum PartyType {
        TEAM, USER
    }

    public enum Party {
        A, B
    }
}
