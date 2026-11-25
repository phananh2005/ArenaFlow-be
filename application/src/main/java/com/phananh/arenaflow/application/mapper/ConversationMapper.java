package com.phananh.arenaflow.application.mapper;

import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.application.dto.response.ConversationResponse;
import com.phananh.arenaflow.application.dto.response.ConversationSummaryResponse;
import com.phananh.arenaflow.application.dto.response.LastMessagePreviewResponse;
import com.phananh.arenaflow.application.dto.response.PartyBriefResponse;
import com.phananh.arenaflow.application.dto.response.PartyResponse;
import com.phananh.arenaflow.application.support.chat.ConversationAccess;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.chat.ConversationMessage;
import com.phananh.arenaflow.domain.projection.ConversationContextView;
import com.phananh.arenaflow.domain.projection.ConversationListRowView;
import com.phananh.arenaflow.domain.projection.ConversationMessageView;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

import java.util.List;
import java.util.UUID;

/**
 * Response chat dựng từ ngữ cảnh đã nạp (không đọc lại). Che người đã xoá tài khoản ở đây (SQL không che). Đợt luồng A
 * chỉ có loại TEAM_MATCH: field của thương lượng / đơn ứng tuyển = null, {@code source} = SYSTEM.
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ConversationMapper {

    String SOURCE_SYSTEM = "SYSTEM";

    default ConversationResponse toResponse(ConversationContextView context, ConversationAccess access) {
        List<PartyResponse> parties = List.of(
                new PartyResponse(context.getPartyAType(), context.getPartyAId(), context.getPartyAName(),
                        context.getPartyAAvatarUrl(), captainName(context.getPartyACaptainName(), context.getPartyACaptainDeleted())),
                new PartyResponse(context.getPartyBType(), context.getPartyBId(), context.getPartyBName(),
                        context.getPartyBAvatarUrl(), captainName(context.getPartyBCaptainName(), context.getPartyBCaptainDeleted())));
        return new ConversationResponse(context.getId(), context.getContextType(), context.getMatchId(),
                context.getMatchType(), context.getRequestId(), context.getRequestEffectiveStatus(), null, null, null,
                null, SOURCE_SYSTEM, null, parties, partyId(context, access.viewerParty()), access.canSend());
    }

    /** Tin vừa gửi: người gửi là người gọi (cột caller của ngữ cảnh). */
    default ConversationMessageResponse toMessageResponse(ConversationMessage message, ConversationContextView context) {
        return new ConversationMessageResponse(message.getId(), message.getConversationId(),
                partyType(context, message.getSenderParty()), partyId(context, message.getSenderParty()),
                message.getSenderUserId(), MapperSupport.maskName(context.getCallerName(), context.getCallerDeleted()),
                MapperSupport.maskAvatar(context.getCallerAvatarUrl(), context.getCallerDeleted()), message.getMessage(),
                message.isRead(), message.getCreatedAt());
    }

    default ConversationMessageResponse toMessageResponse(ConversationMessageView view, ConversationContextView context) {
        Conversation.Party party = Conversation.Party.valueOf(view.getSenderParty());
        return new ConversationMessageResponse(view.getId(), view.getConversationId(), partyType(context, party),
                partyId(context, party), view.getSenderUserId(),
                MapperSupport.maskName(view.getSenderName(), view.getSenderDeleted()),
                MapperSupport.maskAvatar(view.getSenderAvatarUrl(), view.getSenderDeleted()), view.getMessage(),
                Boolean.TRUE.equals(view.getIsRead()), view.getCreatedAt());
    }

    /** {@code otherParty} = bên còn lại; chưa đọc = có tin của bên kia chưa được phía mình đánh dấu đọc. */
    default ConversationSummaryResponse toSummary(ConversationListRowView row, ConversationAccess access) {
        Conversation.Party viewer = access.viewerParty();
        Conversation.Party other = viewer == Conversation.Party.A ? Conversation.Party.B : Conversation.Party.A;
        LastMessagePreviewResponse lastMessage = row.getLastMessageId() == null ? null
                : new LastMessagePreviewResponse(row.getLastMessageId(), row.getLastContentPreview(),
                partyId(row, Conversation.Party.valueOf(row.getLastSenderParty())),
                MapperSupport.maskName(row.getLastSenderName(), row.getLastSenderDeleted()), row.getLastMessageCreatedAt());
        boolean hasUnread = Boolean.TRUE.equals(viewer == Conversation.Party.A ? row.getUnreadFromB() : row.getUnreadFromA());
        return new ConversationSummaryResponse(row.getId(), row.getContextType(), row.getMatchId(), null,
                row.getStartsAt(), row.getRequestId(), null, null, brief(row, viewer), brief(row, other), lastMessage,
                row.getLastActivityAt(), hasUnread, access.canSend());
    }

    private static PartyBriefResponse brief(ConversationContextView context, Conversation.Party party) {
        return party == Conversation.Party.A
                ? new PartyBriefResponse(context.getPartyAType(), context.getPartyAId(), context.getPartyAName(),
                context.getPartyAAvatarUrl())
                : new PartyBriefResponse(context.getPartyBType(), context.getPartyBId(), context.getPartyBName(),
                context.getPartyBAvatarUrl());
    }

    private static UUID partyId(ConversationContextView context, Conversation.Party party) {
        return party == Conversation.Party.A ? context.getPartyAId() : context.getPartyBId();
    }

    private static String partyType(ConversationContextView context, Conversation.Party party) {
        return party == Conversation.Party.A ? context.getPartyAType() : context.getPartyBType();
    }

    /** Đội đã giải tán không còn đội trưởng → null; đội trưởng đã xoá tài khoản → "Người dùng đã xóa". */
    private static String captainName(String name, Boolean deleted) {
        return name == null ? null : MapperSupport.maskName(name, deleted);
    }
}
