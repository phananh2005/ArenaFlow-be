package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Ngữ cảnh gửi đánh giá: trận, trạng thái hai đội, quyền đội trưởng của người gọi, mỗi phía đã đánh giá chưa. */
public interface RatingContextView extends MatchSidesView {

    UUID getMatchId();

    String getStatus();

    Boolean getAuthorRated();

    Boolean getOpponentRated();
}
