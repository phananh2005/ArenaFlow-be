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
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Cuộc trò chuyện giữa hai bên (mỗi bên là đội hoặc người dùng) trong một ngữ cảnh. Chỉ tạo mới và đọc: trạng thái
 * "nhắn được / chỉ xem" suy ra khi đọc từ ngữ cảnh, không lưu. {@code partyAId}/{@code partyBId} đa hình nên không có
 * khoá ngoại.
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

    /** Loại ngữ cảnh; mỗi giá trị phải có đúng một chính sách quyền tương ứng (thiếu → lỗi khởi động). */
    public enum ContextType {
        TEAM_MATCH
    }

    public enum PartyType {
        TEAM, USER
    }

    /** Bên A = đội tạo kèo / đội tuyển; bên B = đội tham gia / ứng viên. */
    public enum Party {
        A, B
    }
}
