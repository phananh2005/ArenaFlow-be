package com.phananh.arenaflow.application.dto.response;

import java.util.List;
import java.util.UUID;

/** Đầu phòng chat: hai bên (bên A trước), bên của người gọi, có gửi được không; field của loại ngữ cảnh khác = null. */
public record ConversationResponse(UUID conversationId, String contextType, UUID matchId, String matchType,
                                   UUID requestId, String requestStatus, UUID pairingId, String pairingStatus,
                                   UUID applicationId, String applicationStatus, String source, UUID externalMatchId,
                                   List<PartyResponse> parties, UUID viewerPartyId, boolean canSend) {
}
