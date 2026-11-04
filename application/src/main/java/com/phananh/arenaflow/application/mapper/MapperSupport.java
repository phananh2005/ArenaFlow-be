package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.common.DeletedUserMasking;
import com.phananh.arenaflow.domain.entity.converter.SkillLevelsConverter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Chuyển đổi dùng chung cho MapStruct (static → MapStruct gọi trực tiếp theo kiểu nguồn/đích).
 */
public final class MapperSupport {

    private static final SkillLevelsConverter SKILL_LEVELS = new SkillLevelsConverter();

    private MapperSupport() {
    }

    /** Thời gian projection (native timestamptz → Instant) → response UTC. */
    public static OffsetDateTime toUtc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** JSON thô của cột skill_levels → danh sách nhãn. */
    public static List<String> skillLevels(String json) {
        return SKILL_LEVELS.convertToEntityAttribute(json);
    }

    public static String maskName(String displayName, Boolean deleted) {
        return DeletedUserMasking.mask(displayName, null, Boolean.TRUE.equals(deleted)).displayName();
    }

    public static String maskAvatar(String avatarUrl, Boolean deleted) {
        return DeletedUserMasking.mask(null, avatarUrl, Boolean.TRUE.equals(deleted)).avatarUrl();
    }
}
