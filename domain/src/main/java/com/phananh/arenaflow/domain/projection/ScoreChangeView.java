package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Điểm uy tín trước / sau một câu UPDATE … RETURNING; {@code subjectType} = {@code TEAM} | {@code USER}. */
public interface ScoreChangeView {
    String getSubjectType();
    UUID getSubjectId();
    Integer getOldScore();
    Integer getNewScore();
}
