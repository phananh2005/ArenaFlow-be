package com.phananh.arenaflow.application.event.match;

import java.time.LocalDateTime;
import java.util.UUID;

/** Kèo được ghép; {@code acceptedRequestId} null ở kèo luồng B, {@code pairingId} null ở kèo luồng A. */
public record MatchConfirmedEvent(UUID matchId, UUID acceptedRequestId, UUID pairingId, UUID authorTeamId, UUID opponentTeamId,
                                  LocalDateTime occurredAt) {}
