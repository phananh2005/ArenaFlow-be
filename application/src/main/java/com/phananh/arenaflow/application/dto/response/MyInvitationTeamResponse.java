package com.phananh.arenaflow.application.dto.response;

import java.util.List;
import java.util.UUID;

/** Đội mời trong danh sách lời mời của tôi; {@code captainName} là đội trưởng hiện tại. */
public record MyInvitationTeamResponse(UUID teamId, String teamName, String avatarUrl, List<String> skillLevels,
                                       String status, String captainName, long memberCount) {
}
