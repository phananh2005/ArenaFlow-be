package com.phananh.arenaflow.application.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

/** Một dòng "Tin nhắn của tôi"; {@code lastMessage} null khi chưa có tin. */
public record ConversationSummaryResponse(UUID conversationId, String contextType, UUID matchId, UUID externalMatchId,
                                          LocalDateTime startsAt, UUID requestId, UUID pairingId, UUID applicationId,
                                          PartyBriefResponse viewerParty, PartyBriefResponse otherParty,
                                          LastMessagePreviewResponse lastMessage, LocalDateTime lastActivityAt,
                                          boolean hasUnread, boolean canSend) {
}
