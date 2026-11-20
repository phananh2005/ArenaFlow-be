package com.phananh.arenaflow.application.support.storage;

import java.time.Instant;

public record SignedPublicId(String scopePath, Instant signedAt, String nonce, String tag) {
}
