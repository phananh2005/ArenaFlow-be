package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.domain.entity.match.Match;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một nguồn cho mã lỗi tồn tại / trạng thái của kèo. */
@Component
public class MatchGuard {

    /** Không có kèo → MATCH_001 (không khoá). */
    public Match findMatch(UUID matchId) {
        throw new UnsupportedOperationException("Bản tạm — thuộc vòng đời kèo");
    }

    /** OPEN và đã tới hạn bài đăng → nhánh chuyển EXPIRED khi ghi. */
    public boolean isExpiryDue(Match.Status status, LocalDateTime expiresAt, LocalDateTime now) {
        throw new UnsupportedOperationException("Bản tạm — thuộc vòng đời kèo");
    }

    /** EXPIRED (đã lưu) → MATCH_004. */
    public void requireNotExpired(Match.Status status) {
        throw new UnsupportedOperationException("Bản tạm — thuộc vòng đời kèo");
    }

    /** EXPIRED → MATCH_004; CONFIRMED → MATCH_003; khác OPEN → MATCH_005. */
    public void requireOpen(Match match) {
        throw new UnsupportedOperationException("Bản tạm — thuộc vòng đời kèo");
    }
}
