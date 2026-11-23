package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;

/**
 * Lịch sử thành viên (Admin): cột của {@link MemberView} + thời điểm rời đội (null khi còn ACTIVE).
 * Cột thời điểm ({@code leftAt}) là {@code TIMESTAMP} giờ Việt Nam, đọc thẳng thành {@code LocalDateTime}.
 */
public interface MemberHistoryView extends MemberView {
    LocalDateTime getLeftAt();
}
