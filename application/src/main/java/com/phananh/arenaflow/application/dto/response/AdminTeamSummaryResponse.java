package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * TeamSummaryResponse + inactiveReason; tên captain/người giải tán đã xoá tài khoản giữ nguyên, kèm cờ để giao diện
 * Admin gắn nhãn.
 *
 * @param teamId               id đội
 * @param teamName             tên đội
 * @param avatarUrl            ảnh đại diện; có thể null
 * @param skillLevels          nhãn phong cách/trình độ; list rỗng khi không có
 * @param status               {@code ACTIVE}, {@code INACTIVE} hoặc {@code DISBANDED}
 * @param inactiveReason       lý do Admin tạm ngưng; null khi chưa từng tạm ngưng hoặc đã kích hoạt lại
 * @param captainName          tên thật đội trưởng; null khi đội không còn đội trưởng
 * @param isCaptainDeleted     true khi đội trưởng đã xoá tài khoản; false khi {@code captainName} null
 * @param memberCount          số thành viên ACTIVE, gồm đội trưởng
 * @param createdAt            thời điểm tạo đội, giờ Việt Nam không kèm múi giờ
 * @param disbandedByName      tên thật người giải tán; null khi chưa giải tán
 * @param isDisbandedByDeleted true khi người giải tán đã xoá tài khoản; false khi {@code disbandedByName} null
 */
public record AdminTeamSummaryResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels,
                                       String status, String inactiveReason, String captainName,
                                       boolean isCaptainDeleted, long memberCount, LocalDateTime createdAt,
                                       String disbandedByName, boolean isDisbandedByDeleted) {
}
