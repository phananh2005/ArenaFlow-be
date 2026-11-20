package com.phananh.arenaflow.application.support.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "arenaflow.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret, String folder) {

    public boolean isConfigured() {
        return hasText(cloudName) && hasText(apiKey) && hasText(apiSecret) && hasText(folder);
    }

    // Không bao giờ in API secret; chỉ cho biết đã đặt hay chưa.
    @Override
    public String toString() {
        return "CloudinaryProperties[cloudName=" + cloudName + ", apiKey=" + apiKey
                + ", apiSecret=" + (hasText(apiSecret) ? "***" : "<empty>") // allow-secret: chuỗi che giá trị
                + ", folder=" + folder + "]";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
