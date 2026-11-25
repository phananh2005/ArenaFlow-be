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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tin nhắn của một cuộc trò chuyện. Giữ người thực sự gửi ({@code senderUserId}) để không đổi khi đội chuyển quyền
 * đội trưởng; bên gửi là A/B của cuộc trò chuyện.
 */
@Entity
@Table(name = "conversation_message")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ConversationMessage {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "sender_party", nullable = false, length = 1)
    private Conversation.Party senderParty;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Setter(AccessLevel.NONE)
    private LocalDateTime createdAt;

    /** Gán {@code createdAt} = giờ hiện tại ({@link AppTime#now()}) khi lưu tin nhắn lần đầu. */
    @PrePersist
    protected void onCreate() {
        createdAt = AppTime.now();
    }
}
