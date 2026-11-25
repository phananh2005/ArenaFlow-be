package com.phananh.arenaflow.application.support.chat;

import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.entity.match.MatchRequest;
import com.phananh.arenaflow.domain.entity.match.Pairing;
import com.phananh.arenaflow.domain.entity.team.Team;
import com.phananh.arenaflow.domain.projection.ConversationContextView;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Chat giữa hai đội trong một kèo. Bên của người gọi (thứ tự): đội trưởng ACTIVE đội A (đội tạo kèo — người làm đội
 * trưởng cả hai đội nhận bên A) → đội trưởng ACTIVE đội B → người đã giải tán đội A / đội B (chỉ xem) → không tham gia.
 * Nhắn được khi đội tham gia là đội đã ghép của kèo (giữ cả khi kèo bị huỷ sau đó), hoặc đội đó còn đang chờ kết quả
 * trên kèo chưa tới hạn — kèo luồng A: yêu cầu gần nhất PENDING; kèo luồng B: thương lượng NEGOTIATING. Trạng thái đội /
 * bộ môn không ảnh hưởng.
 */
@Component
public class TeamMatchConversationPolicy implements ConversationAccessPolicy {

    /** Chính sách này áp cho cuộc trò chuyện giữa hai đội trong một kèo. */
    @Override
    public Conversation.ContextType contextType() {
        return Conversation.ContextType.TEAM_MATCH;
    }

    /** Bên của người gọi, quyền gửi, cờ phải chuyển kèo EXPIRED khi gửi và cờ chỉ xem — tính trong bộ nhớ từ ngữ cảnh. */
    @Override
    public ConversationAccess resolve(ConversationContextView context, UUID callerId, LocalDateTime now) {
        Conversation.Party viewer;
        boolean readOnly = false;
        if (Boolean.TRUE.equals(context.getCallerPartyACaptain())) {
            viewer = Conversation.Party.A;
        } else if (Boolean.TRUE.equals(context.getCallerPartyBCaptain())) {
            viewer = Conversation.Party.B;
        } else if (disbandedBy(context.getPartyATeamStatus(), context.getCallerPartyADisbandedBy())) {
            viewer = Conversation.Party.A;
            readOnly = true;
        } else if (disbandedBy(context.getPartyBTeamStatus(), context.getCallerPartyBDisbandedBy())) {
            viewer = Conversation.Party.B;
            readOnly = true;
        } else {
            return ConversationAccess.NONE;
        }

        boolean matched = context.getPartyBId().equals(context.getMatchOpponentTeamId());
        boolean waiting = isWaiting(context);
        boolean expiryDue = !readOnly && waiting && Match.Status.OPEN.name().equals(context.getMatchStatus())
                && !context.getExpiresAt().isAfter(now);
        boolean canSend = !readOnly && (matched || (waiting && !expiryDue));
        return new ConversationAccess(viewer, canSend, expiryDue, readOnly);
    }

    /**
     * Đội tham gia còn chờ kết quả (trạng thái DB): kèo luồng A — yêu cầu gần nhất PENDING; kèo luồng B — thương lượng
     * NEGOTIATING.
     */
    private static boolean isWaiting(ConversationContextView context) {
        if (Match.Type.HAS_VENUE.name().equals(context.getMatchType())) {
            return MatchRequest.Status.PENDING.name().equals(context.getRequestStatus());
        }
        if (Match.Type.NEED_VENUE.name().equals(context.getMatchType())) {
            return Pairing.Status.NEGOTIATING.name().equals(context.getPairingStatus());
        }
        return false;
    }

    /** Đội đã giải tán và người gọi là người bấm giải tán. */
    private static boolean disbandedBy(String teamStatus, Boolean callerDisbanded) {
        return Team.TeamStatus.DISBANDED.name().equals(teamStatus) && Boolean.TRUE.equals(callerDisbanded);
    }
}
