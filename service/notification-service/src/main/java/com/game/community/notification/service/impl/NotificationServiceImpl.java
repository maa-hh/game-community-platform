package com.game.community.notification.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.notification.NotificationCategory;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.notification.NotificationMessage;
import com.game.community.model.entity.notification.NotificationUserState;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.message.DanmakuEvent;
import com.game.community.feign.UserFeignClient;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.vo.notification.NotificationActorVO;
import com.game.community.model.vo.notification.NotificationCategorySummaryVO;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.notification.mapper.NotificationMessageMapper;
import com.game.community.notification.mapper.NotificationUserStateMapper;
import com.game.community.notification.service.NotificationService;
import com.game.community.notification.service.SseService;
import com.game.community.notification.stream.NotificationAggregatePayload;
import com.game.community.notification.stream.NotificationAggregatePayloadCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMessageMapper notificationMessageMapper;

    private final NotificationUserStateMapper notificationUserStateMapper;

    private final SseService sseService;

    private final UserFeignClient userFeignClient;

    private final ContentFeignClient contentFeignClient;

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
    public List<NotificationCategorySummaryVO> getCategorySummaries(Long userId) {
        List<NotificationCategorySummaryVO> list = new ArrayList<>();
        for (String category : List.of(
                NotificationCategory.SYSTEM,
                NotificationCategory.LIKE_FAVORITE,
                NotificationCategory.FOLLOW,
                NotificationCategory.COMMENT)) {
            NotificationCategorySummaryVO item = new NotificationCategorySummaryVO();
            item.setCategory(category);
            item.setUnreadCount(countUnreadByCategory(userId, category));
            list.add(item);
        }
        return list;
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
        enrichAccountIds(records, result.getRecords());
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<NotificationMessageVO> listMessagesByCategory(Long userId, Long page, Long size, String category) {
        List<Integer> eventTypes = NotificationCategory.eventTypesOf(category);
        if (eventTypes.isEmpty()) {
            return PageResult.of(List.of(), normalizePage(page), normalizeSize(size), 0L);
        }
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<NotificationMessage> result = notificationMessageMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<NotificationMessage>()
                        .eq(NotificationMessage::getUserId, userId)
                        .in(NotificationMessage::getEventType, eventTypes)
                        .orderByDesc(NotificationMessage::getCreateTime)
                        .orderByDesc(NotificationMessage::getId));
        List<NotificationMessageVO> records = result.getRecords().stream().map(this::toVO).toList();
        enrichAccountIds(records, result.getRecords());
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public List<NotificationMessageVO> listMessagesAfterId(Long userId, Long afterId, int limit) {
        if (userId == null || afterId == null || afterId < 0) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<NotificationMessage> messages = notificationMessageMapper.selectList(
                new LambdaQueryWrapper<NotificationMessage>()
                        .eq(NotificationMessage::getUserId, userId)
                        .gt(NotificationMessage::getId, afterId)
                        .orderByAsc(NotificationMessage::getId)
                        .last("LIMIT " + safeLimit));
        List<NotificationMessageVO> records = messages.stream().map(this::toVO).toList();
        enrichAccountIds(records, messages);
        return records;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSummaryVO markAllAsRead(Long userId) {
        if (userId == null) {
            return new NotificationSummaryVO(0L, false);
        }
        ensureUserState(userId);
        NotificationUserState state = lockUserState(userId);
        LocalDateTime now = LocalDateTime.now();
        notificationMessageMapper.update(null, new LambdaUpdateWrapper<NotificationMessage>()
                .eq(NotificationMessage::getUserId, userId)
                .eq(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.UNREAD)
                .set(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.READ)
                .set(NotificationMessage::getReadTime, now));
        state.setUnreadNotificationCount(0L);
        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)
                .set(NotificationUserState::getUnreadNotificationCount, 0L)
                .set(NotificationUserState::getUpdateTime, now));
        NotificationSummaryVO summary = toSummary(state);
        sendSummaryAfterCommit(userId, summary);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSummaryVO markCategoryAsRead(Long userId, String category) {
        List<Integer> eventTypes = NotificationCategory.eventTypesOf(category);
        if (userId == null || eventTypes.isEmpty()) {
            return getSummary(userId);
        }
        ensureUserState(userId);
        NotificationUserState state = lockUserState(userId);
        LocalDateTime now = LocalDateTime.now();
        int affected = notificationMessageMapper.update(null, new LambdaUpdateWrapper<NotificationMessage>()
                .eq(NotificationMessage::getUserId, userId)
                .in(NotificationMessage::getEventType, eventTypes)
                .eq(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.UNREAD)
                .set(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.READ)
                .set(NotificationMessage::getReadTime, now));
        long currentUnread = state.getUnreadNotificationCount() == null ? 0L : state.getUnreadNotificationCount();
        long nextUnread = Math.max(0L, currentUnread - affected);
        state.setUnreadNotificationCount(nextUnread);
        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)
                .set(NotificationUserState::getUnreadNotificationCount, nextUnread)
                .set(NotificationUserState::getUpdateTime, now));
        NotificationSummaryVO summary = toSummary(state);
        sendSummaryAfterCommit(userId, summary);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationSummaryVO markFeedRead(Long userId) {
        if (userId == null) {
            return new NotificationSummaryVO(0L, false);
        }
        ensureUserState(userId);
        NotificationUserState state = lockUserState(userId);
        LocalDateTime now = LocalDateTime.now();
        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, userId)
                .set(NotificationUserState::getFeedUnreadFlag, 0)
                .set(NotificationUserState::getLastFeedReadTime, now)
                .set(NotificationUserState::getUpdateTime, now));
        state.setFeedUnreadFlag(0);
        state.setLastFeedReadTime(now);
        NotificationSummaryVO summary = toSummary(state);
        sendSummaryAfterCommit(userId, summary);
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationMessageVO consumeNotificationEvent(NotificationEventMessage event) {
        if (event == null || event.getRecipientUserId() == null || event.getEventType() == null) {
            return null;
        }
        ensureUserState(event.getRecipientUserId());
        NotificationUserState state = lockUserState(event.getRecipientUserId());
        if (event.getEventType() == NotificationConstants.EventType.FEED_UNREAD) {
            LocalDateTime eventTime = defaultTime(event.getOccurredAt());
            LocalDateTime lastRead = state.getLastFeedReadTime();
            if (lastRead != null && eventTime.isBefore(lastRead)) {
                return null;
            }
            notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                    .eq(NotificationUserState::getUserId, event.getRecipientUserId())
                    .set(NotificationUserState::getFeedUnreadFlag, 1)
                    .set(NotificationUserState::getLastFeedEventTime, eventTime)
                    .set(NotificationUserState::getUpdateTime, LocalDateTime.now()));
            state.setFeedUnreadFlag(1);
            state.setLastFeedEventTime(eventTime);
            sendFeedUnreadAfterCommit(event.getRecipientUserId(), toSummary(state));
            return null;
        }

        NotificationMessage message = new NotificationMessage();
        String eventId = eventIdOf(event);
        LocalDateTime occurredAt = defaultTime(event.getOccurredAt());
        message.setEventId(eventId);
        message.setAggregateKey(NotificationCategory.isAggregatable(event.getEventType())
                ? com.game.community.notification.stream.NotificationAggregateKey.build(event)
                : eventId);
        message.setUserId(event.getRecipientUserId());
        message.setEventType(event.getEventType());
        message.setActorUserId(event.getActorUserId());
        message.setActorUsername(defaultText(event.getActorUsername()));
        message.setActorAvatar(defaultText(event.getActorAvatar()));
        message.setArticleId(event.getArticleId());
        message.setCommentId(event.getCommentId());
        message.setReplyId(event.getReplyId());
        message.setDanmakuId(event.getDanmakuId());
        message.setVideoPublicId(event.getVideoPublicId());
        message.setReportId(event.getReportId());
        message.setTargetUserId(event.getTargetUserId());
        message.setPreviewText(defaultText(event.getPreviewText()));
        message.setResultText(defaultText(event.getResultText()));
        message.setRouteType(event.getRouteType() == null ? NotificationConstants.RouteType.NONE : event.getRouteType());
        message.setReadStatus(NotificationConstants.ReadStatus.UNREAD);
        message.setReadTime(LocalDateTime.of(1970, 1, 1, 0, 0));
        message.setOccurredAt(occurredAt);
        message.setCreateTime(LocalDateTime.now());
        int inserted = notificationMessageMapper.insertIgnore(message);
        if (inserted == 0) {
            log.debug("跳过重复通知事件: recipient={}, eventId={}", event.getRecipientUserId(), eventId);
            return null;
        }

        long currentUnread = state.getUnreadNotificationCount() == null ? 0L : state.getUnreadNotificationCount();
        long nextUnread = currentUnread + 1;
        state.setUnreadNotificationCount(nextUnread);
        notificationUserStateMapper.update(null, new LambdaUpdateWrapper<NotificationUserState>()
                .eq(NotificationUserState::getUserId, event.getRecipientUserId())
                .set(NotificationUserState::getUnreadNotificationCount, nextUnread)
                .set(NotificationUserState::getUpdateTime, LocalDateTime.now()));
        NotificationMessage persisted = notificationMessageMapper.selectByEventId(event.getRecipientUserId(), eventId);
        if (persisted != null) {
            message = persisted;
        }
        NotificationSummaryVO summary = toSummary(state);
        NotificationMessageVO vo = toBaseVO(message);
        if (event.getActorAccountId() != null) {
            vo.setActorAccountId(event.getActorAccountId());
        }
        if (event.getTargetAccountId() != null) {
            vo.setTargetAccountId(event.getTargetAccountId());
        }
        sendNotificationAfterCommit(event.getRecipientUserId(), vo, summary);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeDanmakuEvent(DanmakuEvent event) {
        if (event == null || event.getEventId() == null || event.getArticleId() == null
                || event.getAccountId() == null || event.getId() == null) {
            return;
        }
        Result<ArticleDetailVO> articleResult = contentFeignClient.getArticleDetail(event.getArticleId());
        if (articleResult == null || articleResult.getCode() == null || articleResult.getCode() != 200) {
            throw new IllegalStateException("解析弹幕所属帖子失败，等待 Kafka 重试");
        }
        ArticleDetailVO article = articleResult.getData();
        if (article == null || article.getAuthorAccountId() == null
                || article.getAuthorAccountId().equals(event.getAccountId())) {
            return;
        }
        Result<UserCardInternalVO> recipientResult =
                userFeignClient.getUserByAccountId(article.getAuthorAccountId());
        if (recipientResult == null || recipientResult.getCode() == null || recipientResult.getCode() != 200) {
            throw new IllegalStateException("解析弹幕通知接收人失败，等待 Kafka 重试");
        }
        UserCardInternalVO recipient = recipientResult.getData();
        if (recipient == null || recipient.getUserId() == null) {
            return;
        }

        NotificationEventMessage notification = new NotificationEventMessage();
        notification.setEventId("danmaku-notification:" + event.getEventId());
        notification.setEventType(NotificationConstants.EventType.DANMAKU_COMMENT);
        notification.setRecipientUserId(recipient.getUserId());
        notification.setActorAccountId(event.getAccountId());
        notification.setActorUsername(defaultText(event.getUsernameSnapshot()));
        notification.setActorAvatar(defaultText(event.getAvatarSnapshot()));
        notification.setArticleId(event.getArticleId());
        notification.setDanmakuId(event.getId());
        notification.setVideoPublicId(event.getVideoPublicId());
        notification.setRouteType(NotificationConstants.RouteType.DANMAKU);
        notification.setPreviewText(notification.getActorUsername() + " 发了弹幕");
        notification.setResultText(defaultText(event.getContent()));
        notification.setOccurredAt(defaultTime(event.getEventTime()));
        consumeNotificationEvent(notification);
    }

    private long countUnreadByCategory(Long userId, String category) {
        if (userId == null) {
            return 0L;
        }
        List<Integer> eventTypes = NotificationCategory.eventTypesOf(category);
        if (eventTypes.isEmpty()) {
            return 0L;
        }
        return notificationMessageMapper.selectCount(new LambdaQueryWrapper<NotificationMessage>()
                .eq(NotificationMessage::getUserId, userId)
                .in(NotificationMessage::getEventType, eventTypes)
                .eq(NotificationMessage::getReadStatus, NotificationConstants.ReadStatus.UNREAD));
    }

    private void ensureUserState(Long userId) {
        if (userId == null) {
            return;
        }
        notificationUserStateMapper.insertIgnore(userId);
    }

    private String eventIdOf(NotificationEventMessage event) {
        if (StringUtils.hasText(event.getEventId())) {
            return event.getEventId();
        }
        String businessKey = String.join("|",
                String.valueOf(event.getRecipientUserId()),
                String.valueOf(event.getEventType()),
                String.valueOf(event.getActorUserId()),
                String.valueOf(event.getArticleId()),
                String.valueOf(event.getCommentId()),
                String.valueOf(event.getReplyId()),
                String.valueOf(event.getDanmakuId()),
                String.valueOf(event.getVideoPublicId()),
                String.valueOf(event.getReportId()),
                String.valueOf(event.getOccurredAt()),
                defaultText(event.getPreviewText()),
                defaultText(event.getResultText()));
        return "legacy:" + java.util.UUID.nameUUIDFromBytes(
                businessKey.getBytes(StandardCharsets.UTF_8));
    }

    private NotificationUserState lockUserState(Long userId) {
        NotificationUserState state = notificationUserStateMapper.selectByUserIdForUpdate(userId);
        if (state != null) {
            return state;
        }
        ensureUserState(userId);
        return notificationUserStateMapper.selectByUserIdForUpdate(userId);
    }

    private NotificationSummaryVO toSummary(NotificationUserState state) {
        return new NotificationSummaryVO(
                state.getUnreadNotificationCount() == null ? 0L : state.getUnreadNotificationCount(),
                state.getFeedUnreadFlag() != null && state.getFeedUnreadFlag() == 1
        );
    }

    private NotificationMessageVO toVO(NotificationMessage message) {
        NotificationMessageVO vo = toBaseVO(message);
        if (message.getArticleId() != null) {
            try {
                ArticleDetailVO article = Optional.ofNullable(contentFeignClient.getArticleDetail(message.getArticleId()))
                        .map(Result::getData)
                        .orElse(null);
                vo.setArticlePublicId(article == null ? null : article.getPublicId());
            } catch (Exception e) {
                log.warn("解析通知文章信息失败: articleId={}", message.getArticleId(), e);
            }
        }
        return vo;
    }

    private NotificationMessageVO toBaseVO(NotificationMessage message) {
        NotificationMessageVO vo = new NotificationMessageVO();
        vo.setId(message.getId());
        vo.setEventType(message.getEventType());
        vo.setActorUsername(message.getActorUsername());
        vo.setActorAvatar(message.getActorAvatar());
        vo.setArticleId(message.getArticleId());
        vo.setCommentId(message.getCommentId());
        vo.setReplyId(message.getReplyId());
        vo.setDanmakuId(message.getDanmakuId());
        vo.setVideoPublicId(message.getVideoPublicId());
        vo.setReportId(message.getReportId());
        vo.setPreviewText(message.getPreviewText());
        vo.setResultText(message.getResultText());
        vo.setRouteType(message.getRouteType());
        vo.setReadStatus(message.getReadStatus());
        LocalDateTime readTime = message.getReadTime();
        vo.setReadTime(LocalDateTime.of(1970, 1, 1, 0, 0).equals(readTime) ? null : readTime);
        vo.setCreateTime(message.getCreateTime());
        NotificationAggregatePayload payload =
                NotificationAggregatePayloadCodec.decode(message.getResultText());
        if (payload != null && payload.getActors() != null && !payload.getActors().isEmpty()) {
            vo.setAggregated(true);
            vo.setAggregateActors(payload.getActors());
            vo.setAggregateTotal(payload.getTotal() == null ? payload.getActors().size() : payload.getTotal());
            vo.setAggregateHasLike(Boolean.TRUE.equals(payload.getHasLike())
                    || payload.getActors().stream().anyMatch(actor -> "like".equals(actor.getAction())));
            vo.setAggregateHasFavorite(Boolean.TRUE.equals(payload.getHasFavorite())
                    || payload.getActors().stream().anyMatch(actor -> "favorite".equals(actor.getAction())));
        } else {
            vo.setAggregated(false);
        }
        return vo;
    }

    private void sendNotificationAfterCommit(Long userId, NotificationMessageVO message,
                                              NotificationSummaryVO summary) {
        afterCommit(() -> sseService.sendNotification(userId, message, summary));
    }

    private void sendSummaryAfterCommit(Long userId, NotificationSummaryVO summary) {
        afterCommit(() -> sseService.sendSummary(userId, summary));
    }

    private void sendFeedUnreadAfterCommit(Long userId, NotificationSummaryVO summary) {
        afterCommit(() -> sseService.sendFeedUnread(userId, summary));
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.warn("通知 SSE 推送失败", e);
                }
            }
        });
    }

    private void enrichAccountIds(List<NotificationMessageVO> vos, List<NotificationMessage> messages) {
        if (vos == null || vos.isEmpty() || messages == null || messages.isEmpty()) {
            return;
        }
        Set<Long> userIds = new HashSet<>();
        for (NotificationMessage message : messages) {
            if (message.getActorUserId() != null && message.getActorUserId() > 0) {
                userIds.add(message.getActorUserId());
            }
            if (message.getTargetUserId() != null && message.getTargetUserId() > 0) {
                userIds.add(message.getTargetUserId());
            }
        }
        Map<Long, Long> accountIdMap = resolveAccountIdMap(userIds);
        for (int i = 0; i < messages.size(); i++) {
            NotificationMessage message = messages.get(i);
            NotificationMessageVO vo = vos.get(i);
            Long actorAccountId = message.getActorUserId() == null
                    ? null
                    : accountIdMap.get(message.getActorUserId());
            if (actorAccountId != null) {
                vo.setActorAccountId(actorAccountId);
            }
            Long targetAccountId = message.getTargetUserId() == null
                    ? null
                    : accountIdMap.get(message.getTargetUserId());
            if (targetAccountId != null) {
                vo.setTargetAccountId(targetAccountId);
            }
        }
    }

    private Map<Long, Long> resolveAccountIdMap(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            Result<List<UserCardInternalVO>> result = userFeignClient.getUsersByUserIds(new ArrayList<>(userIds));
            if (result == null || result.getData() == null || result.getData().isEmpty()) {
                return Map.of();
            }
            Map<Long, Long> map = new HashMap<>();
            for (UserCardInternalVO card : result.getData()) {
                if (card.getUserId() != null && card.getAccountId() != null) {
                    map.put(card.getUserId(), card.getAccountId());
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("批量解析通知 accountId 失败", e);
            return Map.of();
        }
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
