package com.phananh.ArenaFlow.dto.response;

/** Phần tử providers của ProfileResponse; provider viết HOA (GOOGLE|FACEBOOK|PHONE). */
public record ProviderResponse(String provider, String providerUserId) {
}
