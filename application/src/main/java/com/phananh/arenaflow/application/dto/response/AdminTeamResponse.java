package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Chi tiết đội cho Admin: field như {@link TeamResponse} nhưng tên captain/người giải tán đã xoá tài khoản giữ nguyên,
 * kèm cờ để giao diện gắn nhãn. captainId/captainName null khi đội không còn captain ACTIVE. Thời điểm là giờ Việt Nam
 * không kèm múi giờ.
 *
 * @param teamId               id đội
 * @param teamName             tên đội
 * @param avatarUrl            ảnh đại diện; có thể null
 * @param description          mô tả; có thể null
 * @param skillLevels          nhãn phong cách/trình độ; list rỗng khi không có
 * @param status               {@code ACTIVE}, {@code INACTIVE} hoặc {@code DISBANDED}
 * @param inactiveReason       lý do Admin tạm ngưng; null khi chưa từng tạm ngưng hoặc đã kích hoạt lại
 * @param captainId            đội trưởng đang hoạt động; null khi đội không còn đội trưởng
 * @param captainName          tên thật đội trưởng; null cùng {@code captainId}
 * @param isCaptainDeleted     true khi đội trưởng đã xoá tài khoản; false khi {@code captainName} null
 * @param memberCount          số thành viên ACTIVE, gồm đội trưởng
 * @param createdBy            người tạo đội
 * @param createdAt            thời điểm tạo đội
 * @param updatedAt            thời điểm sửa gần nhất
 * @param disbandedAt          thời điểm giải tán; null khi chưa giải tán
 * @param disbandedById        người giải tán; null khi chưa giải tán
 * @param disbandedByName      tên thật người giải tán; null khi chưa giải tán
 * @param isDisbandedByDeleted true khi người giải tán đã xoá tài khoản; false khi {@code disbandedByName} null
 * @param disbandReason        lý do giải tán; null khi chưa giải tán
 */
public record AdminTeamResponse(UUID teamId, String teamName, String avatarUrl, String description,
                                List<String> skillLevels, String status, String inactiveReason, UUID captainId,
                                String captainName, boolean isCaptainDeleted, long memberCount, UUID createdBy,
                                LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime disbandedAt,
                                UUID disbandedById, String disbandedByName, boolean isDisbandedByDeleted,
                                String disbandReason) {
}
