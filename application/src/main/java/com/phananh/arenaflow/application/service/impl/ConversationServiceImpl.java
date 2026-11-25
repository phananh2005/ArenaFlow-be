package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.ChatHistoryQuery;
import com.phananh.arenaflow.application.dto.request.SendConversationMessageRequest;
import com.phananh.arenaflow.application.dto.response.ChatHistoryResponse;
import com.phananh.arenaflow.application.dto.response.ConversationMessageResponse;
import com.phananh.arenaflow.application.dto.response.ConversationResponse;
import com.phananh.arenaflow.application.dto.response.ConversationSummaryResponse;
import com.phananh.arenaflow.application.dto.response.PageResponse;
import com.phananh.arenaflow.application.dto.response.PaginationMeta;
import com.phananh.arenaflow.application.mapper.ConversationMapper;
import com.phananh.arenaflow.application.service.ConversationService;
import com.phananh.arenaflow.application.support.ConcurrencyConflictRetrier;
import com.phananh.arenaflow.application.support.MatchExpiry;
import com.phananh.arenaflow.application.support.MatchWriteOutcome;
import com.phananh.arenaflow.application.support.PageRequestFactory;
import com.phananh.arenaflow.application.support.chat.ChatRealtimeGateway;
import com.phananh.arenaflow.application.support.chat.ConversationAccess;
import com.phananh.arenaflow.application.support.chat.ConversationAccessPolicy;
import com.phananh.arenaflow.application.support.chat.RealtimeFrame;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.entity.chat.Conversation;
import com.phananh.arenaflow.domain.entity.chat.ConversationMessage;
import com.phananh.arenaflow.domain.entity.match.Match;
import com.phananh.arenaflow.domain.projection.ChatCursorView;
import com.phananh.arenaflow.domain.projection.ConversationContextView;
import com.phananh.arenaflow.domain.projection.ConversationListRowView;
import com.phananh.arenaflow.domain.projection.ConversationMessageView;
import com.phananh.arenaflow.domain.projection.MineCountView;
import com.phananh.arenaflow.domain.repository.ConversationMessageRepository;
import com.phananh.arenaflow.domain.repository.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;
    private final Map<Conversation.ContextType, ConversationAccessPolicy> policies;
    private final MatchExpiry matchExpiry;
    private final ConcurrencyConflictRetrier concurrencyConflictRetrier;
    private final ConversationMapper conversationMapper;
    private final ChatRealtimeGateway realtimeGateway;
    private final PageRequestFactory pageRequestFactory;
    private final Clock clock;

    /** Mỗi loại ngữ cảnh phải có đúng một chính sách quyền; thiếu → không khởi động được. */
    public ConversationServiceImpl(ConversationRepository conversationRepository,
                                   ConversationMessageRepository messageRepository,
                                   List<ConversationAccessPolicy> policies, MatchExpiry matchExpiry,
                                   ConcurrencyConflictRetrier concurrencyConflictRetrier,
                                   ConversationMapper conversationMapper, ChatRealtimeGateway realtimeGateway,
                                   PageRequestFactory pageRequestFactory, Clock clock) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.policies = new EnumMap<>(Conversation.ContextType.class);
        policies.forEach(policy -> this.policies.put(policy.contextType(), policy));
        for (Conversation.ContextType type : Conversation.ContextType.values()) {
            if (!this.policies.containsKey(type)) {
                throw new IllegalStateException("Missing conversation access policy for " + type);
            }
        }
        this.matchExpiry = matchExpiry;
        this.concurrencyConflictRetrier = concurrencyConflictRetrier;
        this.conversationMapper = conversationMapper;
        this.realtimeGateway = realtimeGateway;
        this.pageRequestFactory = pageRequestFactory;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID conversationId, UUID callerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        ConversationContextView context = findContext(conversationId, callerId, now);
        return conversationMapper.toResponse(context, requireParticipant(context, callerId, now));
    }

    @Override
    public ConversationMessageResponse send(UUID conversationId, UUID callerId, SendConversationMessageRequest request) {
        return concurrencyConflictRetrier.execute(ErrorCode.MATCH_014, () -> {
            LocalDateTime now = LocalDateTime.now(clock);
            ConversationContextView context = findContext(conversationId, callerId, now);
            ConversationAccess access = requireParticipant(context, callerId, now);
            if (access.expiryDue()) {
                matchExpiry.expireIfDue(context.getMatchId(), context.getAuthorTeamId(),
                        Match.Type.valueOf(context.getMatchType()), now);
                return MatchWriteOutcome.<ConversationMessageResponse>expired();
            }
            if (!access.canSend()) {
                throw new BusinessException(ErrorCode.CHAT_004);
            }
            // INSERT lúc commit; id UUID v7 + created_at gán khi persist nên response dựng được trước commit.
            ConversationMessage message = messageRepository.save(ConversationMessage.builder()
                    .conversationId(conversationId)
                    .senderParty(access.viewerParty())
                    .senderUserId(callerId)
                    .message(request.content())
                    .build());
            ConversationMessageResponse response = conversationMapper.toMessageResponse(message, context);
            realtimeGateway.publishAfterCommit(conversationId, new RealtimeFrame.MessageCreated(response));
            return MatchWriteOutcome.done(response);
        }).valueOrThrowExpired();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatHistoryResponse history(UUID conversationId, UUID callerId, ChatHistoryQuery query) {
        LocalDateTime now = LocalDateTime.now(clock);
        ConversationContextView context = findContext(conversationId, callerId, now);
        requireParticipant(context, callerId, now);
        int fetch = query.limit() + 1;
        List<ConversationMessageView> rows;
        if (query.before() != null) {
            ChatCursorView cursor = findCursor(conversationId, query.before());
            rows = messageRepository.findBefore(conversationId, cursor.getCreatedAt(), cursor.getId(), fetch);
        } else if (query.after() != null) {
            ChatCursorView cursor = findCursor(conversationId, query.after());
            rows = messageRepository.findAfter(conversationId, cursor.getCreatedAt(), cursor.getId(), fetch);
        } else {
            rows = messageRepository.findLatest(conversationId, fetch);
        }
        boolean hasMore = rows.size() > query.limit();
        List<ConversationMessageResponse> items = rows.subList(0, Math.min(rows.size(), query.limit())).stream()
                .map(row -> conversationMapper.toMessageResponse(row, context))
                .toList();
        return new ChatHistoryResponse(items, hasMore);
    }

    @Override
    @Transactional
    public void markRead(UUID conversationId, UUID callerId) {
        LocalDateTime now = LocalDateTime.now(clock);
        ConversationContextView context = findContext(conversationId, callerId, now);
        ConversationAccess access = requireParticipant(context, callerId, now);
        messageRepository.markOtherPartyRead(conversationId, access.viewerParty().name());
    }

    @Override
    @Transactional(readOnly = true)
    public void requireSubscribable(UUID conversationId, UUID userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        requireParticipant(findContext(conversationId, userId, now), userId, now);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConversationSummaryResponse> listMyConversations(UUID callerId, int page, int pageSize) {
        pageRequestFactory.create(page, pageSize);
        MineCountView count = conversationRepository.countMine(callerId);
        if (Boolean.TRUE.equals(count.getCallerDeleted())) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        long total = count.getTotal() == null ? 0 : count.getTotal();
        if (total == 0) {
            return new PageResponse<>(List.of(), new PaginationMeta(page, pageSize, 0));
        }
        LocalDateTime now = LocalDateTime.now(clock);
        List<ConversationListRowView> rows = conversationRepository.findMine(callerId, now, pageSize,
                (long) (page - 1) * pageSize);
        List<ConversationSummaryResponse> items = new ArrayList<>(rows.size());
        for (ConversationListRowView row : rows) {
            ConversationAccess access = policyFor(row).resolve(row, callerId, now);
            if (access.viewerParty() == null) {
                // Không thể xảy ra theo cách chọn dòng của câu truy vấn — bỏ dòng thay vì lộ cuộc trò chuyện.
                log.warn("Conversation {} listed for caller but caller has no party; skipped", row.getId());
                continue;
            }
            items.add(conversationMapper.toSummary(row, access));
        }
        return new PageResponse<>(items, new PaginationMeta(page, pageSize, total));
    }

    private ConversationContextView findContext(UUID conversationId, UUID callerId, LocalDateTime now) {
        return conversationRepository.findContext(conversationId, callerId, now)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_002));
    }

    /** USER_001 (người gọi đã xoá tài khoản) → CHAT_003 (không tham gia); trả quyền theo chính sách của loại ngữ cảnh. */
    private ConversationAccess requireParticipant(ConversationContextView context, UUID callerId, LocalDateTime now) {
        if (Boolean.TRUE.equals(context.getCallerDeleted())) {
            throw new BusinessException(ErrorCode.USER_001);
        }
        ConversationAccess access = policyFor(context).resolve(context, callerId, now);
        if (access.viewerParty() == null) {
            throw new BusinessException(ErrorCode.CHAT_003);
        }
        return access;
    }

    private ConversationAccessPolicy policyFor(ConversationContextView context) {
        return policies.get(Conversation.ContextType.valueOf(context.getContextType()));
    }

    private ChatCursorView findCursor(UUID conversationId, UUID messageId) {
        return messageRepository.findCursor(conversationId, messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHAT_001));
    }
}
