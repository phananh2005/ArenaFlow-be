package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đơn APPROVED vừa được đánh dấu đã báo trận bị huỷ; đúng một trong hai khoá trận khác null. */
public interface GameCancelNoticeView {
    UUID getId();
    UUID getMatchId();
    UUID getExternalMatchId();
}
