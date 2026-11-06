package com.phananh.arenaflow.common;

public final class DeletedUserMasking {

    private static final String DELETED_USER_NAME = "Người dùng đã xóa";

    private DeletedUserMasking() {
    }

    /**
     * Che thông tin hiển thị của user đã xoá mềm: {@code deleted} = true → tên "Người dùng đã xóa", avatar null; ngược
     * lại trả nguyên giá trị. Chỉ che tên/avatar — id của user vẫn giữ để client tham chiếu.
     */
    public static MaskedUser mask(String displayName, String avatarUrl, boolean deleted) {
        return deleted
                ? new MaskedUser(DELETED_USER_NAME, null)
                : new MaskedUser(displayName, avatarUrl);
    }

    public record MaskedUser(String displayName, String avatarUrl) {
    }
}
