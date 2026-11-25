package com.phananh.arenaflow.domain.projection;

/** Trạng thái đội + cờ "người gọi là đội trưởng ACTIVE" + tên/ảnh/điểm đội + tên người gọi — một câu. */
public interface TeamCaptainContextView {
    String getStatus();
    Boolean getCaptain();
    String getName();
    String getAvatarUrl();
    Integer getReliabilityScore();
    String getUserDisplayName();
    Boolean getUserDeleted();
}
