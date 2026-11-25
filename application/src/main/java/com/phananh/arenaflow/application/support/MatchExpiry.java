package com.phananh.arenaflow.application.support;

import com.phananh.arenaflow.domain.entity.match.Match;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/** Chuyển kèo OPEN đã quá hạn sang EXPIRED (dùng chung cho API ghi và job); phải chạy trong transaction của caller. */
@Component
public class MatchExpiry {

    /** {@code true} nếu chính lời gọi này chuyển kèo sang EXPIRED. */
    public boolean expireIfDue(UUID matchId, UUID authorTeamId, Match.Type type, LocalDateTime now) {
        throw new UnsupportedOperationException("Bản tạm — thuộc vòng đời kèo");
    }
}
