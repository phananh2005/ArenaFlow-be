package com.phananh.arenaflow.domain.entity.chat;

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
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cuộc trò chuyện dùng chung (chat hai đội trong kèo, chat theo đơn ứng tuyển mercenary). Chỉ INSERT + đọc; hai bên đa
 * hình ({@code TEAM} / {@code USER}). Ngữ cảnh đơn ứng tuyển có {@code applicationId} và đúng một trong {@code matchId} /
 * {@code externalMatchId}.
 */
@Entity
@Table(name = "conversation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Conversation {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "context_type", nullable = false, length = 30, updatable = false)
    private ContextType contextType;

    @Column(name = "match_id", updatable = false)
    private UUID matchId;

    @Column(name = "external_match_id", updatable = false)
    private UUID externalMatchId;

    @Column(name = "application_id", updatable = false)
    private UUID applicationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_a_type", nullable = false, length = 10, updatable = false)
    private PartyType partyAType;

    @Column(name = "party_a_id", nullable = false, updatable = false)
    private UUID partyAId;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_b_type", nullable = false, length = 10, updatable = false)
    private PartyType partyBType;

    @Column(name = "party_b_id", nullable = false, updatable = false)
    private UUID partyBId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }

    public enum ContextType {
        TEAM_MATCH, MERCENARY_APPLICATION
    }

    public enum PartyType {
        TEAM, USER
    }

    public enum Party {
        A, B
    }
}
