package com.phananh.arenaflow.application.support.storage;

/** Admin API của storage từ chối lời gọi vì đã hết hạn mức theo giờ. */
public class AssetAdminRateLimitedException extends RuntimeException {

    private final String resetAt;

    public AssetAdminRateLimitedException(String resetAt) {
        super("Storage admin API rate limited; resets at " + resetAt);
        this.resetAt = resetAt;
    }

    public String getResetAt() {
        return resetAt;
    }
}
