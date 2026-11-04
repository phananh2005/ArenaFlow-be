package com.phananh.arenaflow.common;

public final class DeletedUserMasking {

    private static final String DELETED_USER_NAME = "Người dùng đã xóa";

    private DeletedUserMasking() {
    }

    public static MaskedUser mask(String displayName, String avatarUrl, boolean deleted) {
        return deleted
                ? new MaskedUser(DELETED_USER_NAME, null)
                : new MaskedUser(displayName, avatarUrl);
    }

    public record MaskedUser(String displayName, String avatarUrl) {
    }
}
