package com.phananh.arenaflow.domain.projection;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một dòng "trận LEFT JOIN đánh giá giữa hai đội": cột đánh giá null khi trận chưa có đánh giá nào. */
public interface MatchRatingRowView {

    UUID getAuthorTeamId();

    UUID getOpponentTeamId();

    UUID getRatingId();

    UUID getRaterTeamId();

    UUID getRateeTeamId();

    Boolean getNoShow();

    Integer getScore();

    String getComment();

    Integer getRaterTeamScore();

    Integer getRateeTeamScore();

    LocalDateTime getCreatedAt();
}
