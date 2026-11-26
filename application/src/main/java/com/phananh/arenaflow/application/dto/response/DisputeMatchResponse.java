package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;

public record DisputeMatchResponse(String sportName, String status, LocalDateTime startsAt, LocalDateTime endsAt,
                                   String venueName) {
}
