package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/**
 * Ngữ cảnh một cuộc trò chuyện + cờ của người gọi (một câu cho mọi loại ngữ cảnh). Cột của khối nào không thuộc loại
 * ngữ cảnh của dòng thì {@code null}.
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

    // Khối MERCENARY_APPLICATION
    UUID getApplicationId();
    String getApplicationEffectiveStatus();
    UUID getExternalMatchId();
    String getMercenaryTeamName();
    String getMercenaryTeamAvatarUrl();
    String getMercenaryTeamStatus();
    String getMercenaryCaptainName();
    Boolean getMercenaryCaptainDeleted();
    UUID getMercenaryCaptainUserId();
    UUID getMercenaryTeamDisbandedBy();
    String getApplicantName();
    String getApplicantAvatarUrl();
    Boolean getApplicantDeleted();
    Boolean getCallerMercenaryCaptain();
    Boolean getCallerMercenaryDisbandedBy();
    Boolean getCallerApplicant();
}
