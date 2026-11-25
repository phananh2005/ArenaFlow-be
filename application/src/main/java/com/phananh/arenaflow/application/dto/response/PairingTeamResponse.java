package com.phananh.arenaflow.application.dto.response;

import java.util.UUID;

/**
 * Một đội trong thương lượng; {@code captainName} = đội trưởng hiện tại (đã che khi xoá tài khoản; null khi đội đã
 * giải tán).
 */
public record PairingTeamResponse(UUID teamId, String teamName, String avatarUrl, Integer reliabilityScore,
                                  String captainName) {
}
