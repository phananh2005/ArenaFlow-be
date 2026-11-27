package com.phananh.arenaflow.domain.projection;

/** Context thao tác trên một đơn: {@link ApplicationView} + cờ người gọi + điều kiện ứng viên + trạng thái trận. */
public interface ApplicationContextView extends ApplicationView {
    Boolean getCallerCaptain();
    Boolean getCallerApplicant();
    Boolean getApplicantMember();
    Boolean getApplicantOverlap();
    Boolean getGameCancelled();
    Boolean getGameEnded();
}
