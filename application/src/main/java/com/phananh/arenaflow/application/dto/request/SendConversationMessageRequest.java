package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Nội dung được bỏ khoảng trắng đầu/cuối trước khi kiểm độ dài (1..1000 ký tự). Bên gửi do hệ thống xác định. */
public record SendConversationMessageRequest(@NotBlank @Size(max = 1000) String content) {

    public SendConversationMessageRequest {
        content = content == null ? null : content.trim();
    }
}
