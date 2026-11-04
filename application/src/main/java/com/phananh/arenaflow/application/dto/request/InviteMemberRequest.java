package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record InviteMemberRequest(@NotNull UUID userId) {
}
