package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ngữ cảnh cuộc trò chuyện đọc trong một câu: cột chung + cờ / tên / ảnh người gọi + khối của loại TEAM_MATCH (kèo, hai
 * đội, đội trưởng hiện tại, người đã giải tán, yêu cầu gần nhất / thương lượng của đội tham gia). Khối của loại khác
 * null.
 * {@code requestStatus} là trạng thái DB (chính sách quyền dùng), {@code requestEffectiveStatus} là trạng thái hiển thị.
 * Kèo luồng B: thương lượng của đội tham gia thay cho yêu cầu — {@code pairingStatus} (DB) và
 * {@code pairingEffectiveStatus} (hiển thị: NEGOTIATING của kèo OPEN đã tới hạn → EXPIRED); kèo luồng A null.
 */
public interface ConversationContextView {
    UUID getId();
    String getContextType();
    UUID getMatchId();
    String getPartyAType();
    UUID getPartyAId();
    String getPartyBType();
    UUID getPartyBId();
    String getCallerName();
    String getCallerAvatarUrl();
    Boolean getCallerDeleted();

    String getMatchType();
    String getMatchStatus();
    LocalDateTime getExpiresAt();
    UUID getAuthorTeamId();
    UUID getMatchOpponentTeamId();

    String getPartyAName();
    String getPartyAAvatarUrl();
    String getPartyACaptainName();
    Boolean getPartyACaptainDeleted();
    Boolean getCallerPartyACaptain();
    String getPartyATeamStatus();
    Boolean getCallerPartyADisbandedBy();
    UUID getPartyACaptainUserId();
    UUID getPartyADisbandedBy();

    String getPartyBName();
    String getPartyBAvatarUrl();
    String getPartyBCaptainName();
    Boolean getPartyBCaptainDeleted();
    Boolean getCallerPartyBCaptain();
    String getPartyBTeamStatus();
    Boolean getCallerPartyBDisbandedBy();
    UUID getPartyBCaptainUserId();
    UUID getPartyBDisbandedBy();

    UUID getRequestId();
    String getRequestStatus();
    String getRequestEffectiveStatus();

    UUID getPairingId();
    String getPairingStatus();
    String getPairingEffectiveStatus();
}
