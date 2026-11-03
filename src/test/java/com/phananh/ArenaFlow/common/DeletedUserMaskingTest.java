package com.phananh.ArenaFlow.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeletedUserMaskingTest {

    @Test
    void masksDeletedUsersNameAndAvatar() {
        var masked = DeletedUserMasking.mask("Nguyễn Văn A", "https://example.com/avatar.png", true);

        assertThat(masked.displayName()).isEqualTo("Người dùng đã xóa");
        assertThat(masked.avatarUrl()).isNull();
    }

    @Test
    void preservesActiveUsersNameAndAvatar() {
        var masked = DeletedUserMasking.mask("Nguyễn Văn A", "https://example.com/avatar.png", false);

        assertThat(masked.displayName()).isEqualTo("Nguyễn Văn A");
        assertThat(masked.avatarUrl()).isEqualTo("https://example.com/avatar.png");
    }
}
