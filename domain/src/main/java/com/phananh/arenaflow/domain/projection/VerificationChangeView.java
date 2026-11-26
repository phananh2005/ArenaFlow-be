package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Đội vừa được cấp ({@code verified = true}) hoặc thu hồi huy hiệu xác minh. */
public interface VerificationChangeView {

    UUID getTeamId();

    Boolean getVerified();
}
