package com.phananh.arenaflow.application.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Body không bắt buộc; {@code reason} null/rỗng sau trim → lý do mặc định
 * {@code TeamServiceImpl.DEFAULT_DISBAND_REASON}.
 */
public record DisbandTeamRequest(@Size(max = 100) String reason) {
}
