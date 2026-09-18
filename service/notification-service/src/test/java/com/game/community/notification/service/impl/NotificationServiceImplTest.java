package com.game.community.notification.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.entity.notification.NotificationMessage;
import com.game.community.model.entity.notification.NotificationFeedEvent;
import com.game.community.model.entity.notification.NotificationUserState;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.message.DanmakuEvent;
import com.game.community.model.base.Result;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.notification.mapper.NotificationMessageMapper;
import com.game.community.notification.mapper.NotificationFeedEventMapper;
import com.game.community.notification.mapper.NotificationUserStateMapper;
import com.game.community.notification.service.SseService;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.UserFeignClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationMessageMapper notificationMessageMapper;

    @Mock
    private NotificationFeedEventMapper notificationFeedEventMapper;

    @Mock
    private NotificationUserStateMapper notificationUserStateMapper;

    @Mock
    private SseService sseService;

    @Mock
    private UserFeignClient userFeignClient;

    @Mock
    private ContentFeignClient contentFeignClient;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, NotificationMessage.class);
        TableInfoHelper.initTableInfo(assistant, NotificationUserState.class);
        TableInfoHelper.initTableInfo(assistant, NotificationFeedEvent.class);
        notificationService = new NotificationServiceImpl(
                notificationMessageMapper,
                notificationFeedEventMapper,
                notificationUserStateMapper,
                sseService,
                userFeignClient,
                contentFeignClient);
    }

    @Test
    void consumeNormalNotificationShouldPersistAndPushSummary() {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventType(NotificationConstants.EventType.ARTICLE_COMMENT);
        event.setRecipientUserId(9L);
        event.setActorUserId(8L);
        event.setActorUsername("alice");
        event.setActorAvatar("avatar.png");
        event.setArticleId(100L);
        event.setPreviewText("alice 评论了你的帖子");
        event.setRouteType(NotificationConstants.RouteType.ARTICLE);
        event.setOccurredAt(LocalDateTime.now());

        when(notificationUserStateMapper.selectByUserIdForUpdate(any())).thenReturn(buildState(9L, 2L, 0));
        when(notificationMessageMapper.insertIfAbsent(any())).thenReturn(1);

        NotificationMessageVO result = notificationService.consumeNotificationEvent(event);

        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo(NotificationConstants.EventType.ARTICLE_COMMENT);
        assertThat(result.getPreviewText()).isEqualTo("alice 评论了你的帖子");

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationMessageMapper).insertIfAbsent(messageCaptor.capture());
        NotificationMessage inserted = messageCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(9L);
        assertThat(inserted.getActorUsername()).isEqualTo("alice");
        assertThat(inserted.getRouteType()).isEqualTo(NotificationConstants.RouteType.ARTICLE);
        assertThat(inserted.getReadStatus()).isEqualTo(NotificationConstants.ReadStatus.UNREAD);

        verify(notificationUserStateMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sseService).sendNotification(eq(9L), any(NotificationMessageVO.class), eq(new NotificationSummaryVO(3L, false, 0L)));
        verify(sseService, never()).sendFeedUnread(eq(9L), any());
    }

    @Test
    void consumeFeedUnreadShouldOnlyUpdateStateAndPushFeedEvent() {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventType(NotificationConstants.EventType.FEED_UNREAD);
        event.setRecipientUserId(6L);
        event.setEventId("feed-event-6");
        event.setFeedItemId(600L);
        event.setOccurredAt(LocalDateTime.now());

        when(notificationUserStateMapper.selectByUserIdForUpdate(any())).thenReturn(buildState(6L, 2L, 1));
        when(notificationFeedEventMapper.insertIfAbsent(any(NotificationFeedEvent.class))).thenReturn(1);

        NotificationMessageVO result = notificationService.consumeNotificationEvent(event);

        assertThat(result).isNull();
        verify(notificationMessageMapper, never()).insertIfAbsent(any(NotificationMessage.class));
        verify(notificationUserStateMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sseService).sendFeedUnread(6L, new NotificationSummaryVO(2L, true, 1L));
        verify(sseService, never()).sendNotification(eq(6L), any(), any());
    }

    @Test
    void consumeDanmakuEventShouldNotifyVideoAuthorAndKeepLocation() {
        ArticleDetailVO article = new ArticleDetailVO();
        article.setId(100L);
        article.setAuthorAccountId(9001L);
        UserCardInternalVO recipient = new UserCardInternalVO();
        recipient.setUserId(9L);
        recipient.setAccountId(9001L);
        DanmakuEvent event = new DanmakuEvent();
        event.setId(77L);
        event.setEventId("danmaku-event-77");
        event.setArticleId(100L);
        event.setVideoPublicId("post-100");
        event.setAccountId(8001L);
        event.setUsernameSnapshot("alice");
        event.setContent("这一幕太精彩了");
        event.setEventTime(LocalDateTime.now());

        when(contentFeignClient.getArticleDetail(100L)).thenReturn(Result.success(article));
        when(userFeignClient.getUserByAccountId(9001L)).thenReturn(Result.success(recipient));
        when(notificationUserStateMapper.selectByUserIdForUpdate(any())).thenReturn(buildState(9L, 0L, 0));
        when(notificationMessageMapper.insertIfAbsent(any())).thenReturn(1);

        notificationService.consumeDanmakuEvent(event);

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationMessageMapper).insertIfAbsent(messageCaptor.capture());
        NotificationMessage inserted = messageCaptor.getValue();
        assertThat(inserted.getEventType()).isEqualTo(NotificationConstants.EventType.DANMAKU_COMMENT);
        assertThat(inserted.getDanmakuId()).isEqualTo(77L);
        assertThat(inserted.getVideoPublicId()).isEqualTo("post-100");
        assertThat(inserted.getArticleId()).isEqualTo(100L);
        assertThat(inserted.getUserId()).isEqualTo(9L);
        assertThat(inserted.getResultText()).isEqualTo("这一幕太精彩了");
    }

    @Test
    void markAllAsReadShouldResetUnreadCountAndPushSummary() {
        when(notificationUserStateMapper.selectByUserIdForUpdate(any())).thenReturn(buildState(11L, 0L, 1));

        NotificationSummaryVO summary = notificationService.markAllAsRead(11L);

        assertThat(summary).isEqualTo(new NotificationSummaryVO(0L, true, 0L));
        verify(notificationMessageMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(notificationUserStateMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sseService).sendSummary(11L, new NotificationSummaryVO(0L, true, 0L));
    }

    private NotificationUserState buildState(Long userId, Long unreadCount, Integer feedUnread) {
        NotificationUserState state = new NotificationUserState();
        state.setUserId(userId);
        state.setUnreadNotificationCount(unreadCount);
        state.setFeedUnreadFlag(feedUnread);
        return state;
    }
}
