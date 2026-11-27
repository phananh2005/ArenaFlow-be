package com.phananh.arenaflow.application.service.impl;

import com.phananh.arenaflow.application.dto.request.NotificationListQuery;
import com.phananh.arenaflow.application.dto.response.NotificationHistoryResponse;
import com.phananh.arenaflow.application.dto.response.UnreadCountResponse;
import com.phananh.arenaflow.application.mapper.NotificationMapper;
import com.phananh.arenaflow.application.service.NotificationService;
import com.phananh.arenaflow.application.support.notification.NotificationRules;
import com.phananh.arenaflow.common.exception.BusinessException;
import com.phananh.arenaflow.common.exception.ErrorCode;
import com.phananh.arenaflow.domain.projection.NotificationCallerView;
import com.phananh.arenaflow.domain.projection.NotificationReadContextView;
import com.phananh.arenaflow.domain.projection.NotificationView;
import com.phananh.arenaflow.domain.projection.UnreadCountView;
import com.phananh.arenaflow.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final Clock clock;

    /**
     * Q1 đọc cờ người gọi đã xoá + mốc con trỏ (chạy cả khi không có con trỏ để kiểm người gọi); Q2 chọn một trong sáu
     * câu cố định theo (chưa đọc, con trỏ) với {@code limit + 1} để biết còn trang.
     */
    @Override
    @Transactional(readOnly = true)
    public NotificationHistoryResponse listMine(UUID callerId, NotificationListQuery query) {
        LocalDateTime visibleFrom = visibleFrom();
        UUID anchorId = query.before() != null ? query.before() : query.after();
        NotificationCallerView caller = notificationRepository.findCaller(callerId, anchorId, visibleFrom)
                .filter(view -> !Boolean.TRUE.equals(view.getCallerDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (anchorId != null && caller.getAnchorCreatedAt() == null) {
            // Cùng một mã cho mốc của người khác, quá 30 ngày, đã xoá hay không tồn tại — không tiết lộ.
            throw new BusinessException(ErrorCode.NOTIFICATION_002);
        }
        int fetch = query.limit() + 1;
        LocalDateTime anchorAt = caller.getAnchorCreatedAt();
        boolean unread = query.unreadOnly();
        List<NotificationView> rows;
        if (query.before() != null) {
            rows = unread ? notificationRepository.findBeforeUnread(callerId, visibleFrom, anchorAt, anchorId, fetch)
                    : notificationRepository.findBefore(callerId, visibleFrom, anchorAt, anchorId, fetch);
        } else if (query.after() != null) {
            rows = unread ? notificationRepository.findAfterUnread(callerId, visibleFrom, anchorAt, anchorId, fetch)
                    : notificationRepository.findAfter(callerId, visibleFrom, anchorAt, anchorId, fetch);
        } else {
            rows = unread ? notificationRepository.findLatestUnread(callerId, visibleFrom, fetch)
                    : notificationRepository.findLatest(callerId, visibleFrom, fetch);
        }
        boolean hasMore = rows.size() > query.limit();
        List<NotificationView> page = hasMore ? rows.subList(0, query.limit()) : rows;
        return new NotificationHistoryResponse(page.stream().map(notificationMapper::toResponse).toList(), hasMore);
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse countUnread(UUID callerId) {
        UnreadCountView view = notificationRepository.countUnread(callerId, visibleFrom())
                .filter(row -> !Boolean.TRUE.equals(row.getCallerDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        return new UnreadCountResponse(view.getUnreadCount());
    }

    /** Không khoá, không retry: câu UPDATE lọc {@code NOT is_read} nên hai request đồng thời cho cùng kết quả. */
    @Override
    @Transactional
    public void markRead(UUID callerId, UUID notificationId) {
        NotificationReadContextView context = notificationRepository.findReadContext(callerId, notificationId, visibleFrom())
                .filter(row -> !Boolean.TRUE.equals(row.getCallerDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        if (!Boolean.TRUE.equals(context.getFound())) {
            throw new BusinessException(ErrorCode.NOTIFICATION_001);
        }
        if (Boolean.TRUE.equals(context.getIsRead())) {
            return;
        }
        notificationRepository.markRead(callerId, notificationId);
    }

    @Override
    @Transactional
    public void markAllRead(UUID callerId) {
        LocalDateTime visibleFrom = visibleFrom();
        notificationRepository.findCaller(callerId, null, visibleFrom)
                .filter(view -> !Boolean.TRUE.equals(view.getCallerDeleted()))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_001));
        notificationRepository.markAllRead(callerId, visibleFrom);
    }

    /** Thông báo tạo trước mốc này không hiện ở mọi endpoint, kể cả khi job xoá chưa chạy. */
    private LocalDateTime visibleFrom() {
        return LocalDateTime.now(clock).minus(NotificationRules.RETENTION);
    }
}
