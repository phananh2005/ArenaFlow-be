package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.projection.ConversationContextView;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Quyền trong cuộc trò chuyện theo đơn ứng tuyển. Bên A = đội tuyển (đội trưởng ACTIVE hiện tại gửi được; người đã giải
 * tán đội tuyển chỉ xem); bên B = ứng viên. Gửi được khi đơn (trạng thái hiệu lực) PENDING hoặc APPROVED — không xét
 * trạng thái trận. Không có nhánh chuyển kèo hết hạn.
 */
@Component
public class MercenaryApplicationConversationPolicy implements ConversationAccessPolicy {

    private static final Set<String> SENDABLE = Set.of("PENDING", "APPROVED");
    private static final String DISBANDED = "DISBANDED";

    @Override
    public Conversation.ContextType contextType() {
        return Conversation.ContextType.MERCENARY_APPLICATION;
    }

    /** Thứ tự: đội trưởng ACTIVE → A; ứng viên → B; người đã giải tán đội tuyển → A chỉ xem; còn lại không tham gia. */
    @Override
    public ConversationAccess resolve(ConversationContextView ctx, UUID callerId, LocalDateTime now) {
        boolean activeApplication = SENDABLE.contains(ctx.getApplicationEffectiveStatus());
        if (Boolean.TRUE.equals(ctx.getCallerMercenaryCaptain())) {
            return new ConversationAccess(Conversation.Party.A, activeApplication, false, false);
        }
        if (Boolean.TRUE.equals(ctx.getCallerApplicant())) {
            return new ConversationAccess(Conversation.Party.B, activeApplication, false, false);
        }
        if (DISBANDED.equals(ctx.getMercenaryTeamStatus()) && Boolean.TRUE.equals(ctx.getCallerMercenaryDisbandedBy())) {
            return new ConversationAccess(Conversation.Party.A, false, false, true);
        }
        return new ConversationAccess(null, false, false, false);
    }
}
