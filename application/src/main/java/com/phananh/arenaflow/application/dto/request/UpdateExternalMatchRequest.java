package com.phananh.arenaflow.application.dto.request;

import com.phananh.arenaflow.application.support.mercenary.MercenaryRules;
import com.phananh.arenaflow.application.validation.ValidSkillLevels;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Sửa một phần trận ngoài: field null = giữ nguyên; {@code opponentName} rỗng = xoá. Cặp giờ kiểm ở service sau khi áp
 * thay đổi.
 */
public record UpdateExternalMatchRequest(
        UUID venueId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        @ValidSkillLevels List<String> skillLevels,
        @Size(max = MercenaryRules.MAX_OPPONENT_NAME) String opponentName) {

    public UpdateExternalMatchRequest {
        opponentName = opponentName == null ? null : opponentName.trim();
    }
}
