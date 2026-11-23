package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Tóm tắt đội trong danh sách.
 *
 * @param teamId          id đội
 * @param teamName        tên đội
 * @param avatarUrl       ảnh đại diện; có thể null
 * @param skillLevels     nhãn phong cách/trình độ; list rỗng khi không có
 * @param status          {@code ACTIVE}, {@code INACTIVE} hoặc {@code DISBANDED}
 * @param captainName     tên đội trưởng, đã che khi đã xoá tài khoản; null khi đội không còn đội trưởng
 * @param memberCount     số thành viên ACTIVE, gồm đội trưởng
 * @param createdAt       thời điểm tạo đội, giờ Việt Nam không kèm múi giờ
 * @param disbandedByName tên người giải tán, đã che khi đã xoá tài khoản; null khi chưa giải tán
 */
public record TeamSummaryResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels, String status,
                                  String captainName, long memberCount, LocalDateTime createdAt,
                                  String disbandedByName) {
}
