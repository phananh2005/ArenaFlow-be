package com.phananh.arenaflow.domain.projection;

import java.util.UUID;

/** Chủ thể (đội / cá nhân) vừa đổi trạng thái huy hiệu xác minh. */
public interface VerificationChangeView {
    UUID getSubjectId();
    Boolean getVerified();
}
