package com.game.community.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.notification.NotificationMessage;
import com.game.community.model.entity.notification.NotificationUserState;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.notification.mapper.NotificationMessageMapper;
import com.game.community.notification.mapper.NotificationUserStateMapper;
import com.game.community.notification.service.NotificationService;
import com.game.community.notification.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMessageMapper notificationMessageMapper;

    private final NotificationUserStateMapper notificationUserStateMapper;

    private final SseService sseService;

    @Override
    public NotificationSummaryVO getSummary(Long userId) {
        if (userId == null) {
            return new NotificationSummaryVO(0L, false);
        }
        ensureUserState(userId);
        NotificationUserState state = notificationUserStateMapper.selectOne(new LambdaQueryWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)
                .last("LIMIT 1"));
        if (state == null) {
            return new NotificationSummaryVO(0L, false);
        }
        return toSummary(state);
    }

    @Override
    public PageResult<NotificationMessageVO> listMessages(Long userId, Long page, Long size, Integer eventType) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<NotificationMessage> result = notificationMessageMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<NotificationMessage>()
                        .eq(NotificationMessage::getUserId, userId)
                        .eq(eventType != null, NotificationMessage::getEventType, eventType)
                        .orderByDesc(NotificationMessage::getCreateTime)
                        .orderByDesc(NotificationMessage::getId));
        List<NotificationMessageVO> records = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSummaryVO markAllAsRead(Long userId) {
        if (userId == null) {
            return new NotificationSummaryVO(0L, false);
        }
        ensureUserState(userId);
        LocalDateTime now = LocalDateTime.now();
        notificationMessageMapper.update(null, new LambdaUpdateWrapper<NotificationMessage>()
                .eq(NotificationMessage::getUserId, userId)
                .eq(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.UNREAD)
                .set(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.READ)
                .set(NotificationMessage::getReadTime, now));
        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)
                .set(NotificationUserState::getUnreadNotificationCount, 0L)
                .set(NotificationUserState::getUpdateTime, now));
        NotificationSummaryVO summary = getSummary(userId);
        sseService.sendSummary(userId, summary);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSummaryVO markFeedRead(Long userId) {
        if (userId == null) {
            return new NotificationSummaryVO(0L, false);
        }
        ensureUserState(userId);
        LocalDateTime now = LocalDateTime.now();
        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)
                .set(NotificationUserState::getFeedUnreadFlag, 0)
                .set(NotificationUserState::getLastFeedReadTime, now)
                .set(NotificationUserState::getUpdateTime, now));
        NotificationSummaryVO summary = getSummary(userId);
        sseService.sendSummary(userId, summary);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationMessageVO consumeNotificationEvent(NotificationEventMessage event) {
        ensureUserState(event.getRecipientUserId());
        if (event.getEventType() == NotificationConstants.EventType.FEED_UNREAD) {
            notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                    .eq(NotificationUserState::getUserId, event.getRecipientUserId())
                    .set(NotificationUserState::getFeedUnreadFlag, 1)
                    .set(NotificationUserState::getLastFeedEventTime, defaultTime(event.getOccurredAt()))
                    .set(NotificationUserState::getUpdateTime, LocalDateTime.now()));
            NotificationSummaryVO summary = getSummary(event.getRecipientUserId());
            sseService.sendFeedUnread(event.getRecipientUserId(), summary);
            return null;
        }

        NotificationMessage message = new NotificationMessage();
        message.setUserId(event.getRecipientUserId());
        message.setEventType(event.getEventType());
        message.setActorUserId(event.getActorUserId());
        message.setActorUsername(defaultText(event.getActorUsername()));
        message.setActorAvatar(defaultText(event.getActorAvatar()));
        message.setArticleId(event.getArticleId());
        message.setCommentId(event.getCommentId());
        message.setReplyId(event.getReplyId());
        message.setReportId(event.getReportId());
        message.setTargetUserId(event.getTargetUserId());
        message.setPreviewText(defaultText(event.getPreviewText()));
        message.setResultText(defaultText(event.getResultText()));
        message.setRouteType(event.getRouteType() == null ? NotificationConstants.RouteType.NONE : event.getRouteType());
        message.setReadStatus(NotificationConstants.ReadStatus.UNREAD);
        message.setReadTime(null);
        notificationMessageMapper.insert(message);

        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, event.getRecipientUserId())
                .setSql("unread_notification_count = unread_notification_count + 1")
                .set(NotificationUserState::getUpdateTime, LocalDateTime.now()));
        NotificationSummaryVO summary = getSummary(event.getRecipientUserId());
        NotificationMessageVO vo = toVO(message);
        sseService.sendNotification(event.getRecipientUserId(), vo, summary);
        return vo;
    }

    private void ensureUserState(Long userId) {
        if (userId == null) {
            return;
        }
        if (notificationUserStateMapper.selectCount(new LambdaQueryWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)) > 0) {
            return;
        }
        NotificationUserState state = new NotificationUserState();
        state.setUserId(userId);
        state.setUnreadNotificationCount(0L);
        state.setFeedUnreadFlag(0);
        state.setLastFeedEventTime(null);
        state.setLastFeedReadTime(null);
        try {
            notificationUserStateMapper.insert(state);
        } catch (DuplicateKeyException ignored) {
            // 并发创建时由唯一索引兜底。
        }
    }

    private NotificationSummaryVO toSummary(NotificationUserState state) {
        return new NotificationSummaryVO(
                state.getUnreadNotificationCount() == null ? 0L : state.getUnreadNotificationCount(),
                state.getFeedUnreadFlag() != null && state.getFeedUnreadFlag() == 1
        );
    }

    private NotificationMessageVO toVO(NotificationMessage message) {
        NotificationMessageVO vo = new NotificationMessageVO();
        vo.setId(message.getId());
        vo.setEventType(message.getEventType());
        vo.setActorUserId(message.getActorUserId());
        vo.setActorUsername(message.getActorUsername());
        vo.setActorAvatar(message.getActorAvatar());
        vo.setArticleId(message.getArticleId());
        vo.setCommentId(message.getCommentId());
        vo.setReplyId(message.getReplyId());
        vo.setReportId(message.getReportId());
        vo.setTargetUserId(message.getTargetUserId());
        vo.setPreviewText(message.getPreviewText());
        vo.setResultText(message.getResultText());
        vo.setRouteType(message.getRouteType());
        vo.setReadStatus(message.getReadStatus());
        vo.setReadTime(message.getReadTime());
        vo.setCreateTime(message.getCreateTime());
        return vo;
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizeSize(Long size) {
        return size == null || size < 1 ? 20 : Math.min(size, 100);
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "";
    }

    private LocalDateTime defaultTime(LocalDateTime value) {
        return value == null ? LocalDateTime.now() : value;
    }
}
