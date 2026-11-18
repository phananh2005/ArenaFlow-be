package com.phananh.arenaflow.application.support.security;

import java.util.UUID;

/** Authenticated caller identity and Admin role derived from the access-token claims. */
public record CurrentActor(UUID userId, boolean admin) {
}
