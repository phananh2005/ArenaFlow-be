package com.phananh.arenaflow.domain.projection;

import java.time.Instant;

/** Lịch sử thành viên (Admin): cột của {@link MemberView} + thời điểm rời đội (null khi còn ACTIVE). */
public interface MemberHistoryView extends MemberView {
    Instant getLeftAt();
}
