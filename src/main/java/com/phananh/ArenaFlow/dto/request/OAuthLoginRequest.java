package com.phananh.ArenaFlow.dto.request;

import com.phananh.ArenaFlow.validation.OneOf;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /auth/oauth (mock): chấp nhận mọi token không rỗng. */
public record OAuthLoginRequest(
        @NotNull @OneOf({"GOOGLE", "FACEBOOK"}) String provider,
        @NotBlank String oauthToken,
        @NotBlank @Email @Size(max = 255) String email) {
}
