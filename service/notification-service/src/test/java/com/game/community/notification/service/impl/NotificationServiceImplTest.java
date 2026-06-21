package com.game.community.notification.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.model.entity.notification.NotificationMessage;
import com.game.community.model.entity.notification.NotificationUserState;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;
import com.game.community.notification.mapper.NotificationMessageMapper;
import com.game.community.notification.mapper.NotificationUserStateMapper;
import com.game.community.notification.service.SseService;
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
    private NotificationUserStateMapper notificationUserStateMapper;

    @Mock
    private SseService sseService;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, NotificationMessage.class);
        TableInfoHelper.initTableInfo(assistant, NotificationUserState.class);
        notificationService = new NotificationServiceImpl(notificationMessageMapper, notificationUserStateMapper, sseService);
        when(notificationUserStateMapper.selectCount(any())).thenReturn(1L);
        when(notificationUserStateMapper.selectOne(any())).thenReturn(buildState(9L, 2L, 0));
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

        when(notificationUserStateMapper.selectOne(any())).thenReturn(buildState(9L, 3L, 0));

        NotificationMessageVO result = notificationService.consumeNotificationEvent(event);

        assertThat(result).isNotNull();
        assertThat(result.getEventType()).isEqualTo(NotificationConstants.EventType.ARTICLE_COMMENT);
        assertThat(result.getPreviewText()).isEqualTo("alice 评论了你的帖子");

        ArgumentCaptor<NotificationMessage> messageCaptor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(notificationMessageMapper).insert(messageCaptor.capture());
        NotificationMessage inserted = messageCaptor.getValue();
        assertThat(inserted.getUserId()).isEqualTo(9L);
        assertThat(inserted.getActorUsername()).isEqualTo("alice");
        assertThat(inserted.getRouteType()).isEqualTo(NotificationConstants.RouteType.ARTICLE);
        assertThat(inserted.getReadStatus()).isEqualTo(NotificationConstants.ReadStatus.UNREAD);

        verify(notificationUserStateMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sseService).sendNotification(eq(9L), any(NotificationMessageVO.class), eq(new NotificationSummaryVO(3L, false)));
        verify(sseService, never()).sendFeedUnread(eq(9L), any());
    }

    @Test
    void consumeFeedUnreadShouldOnlyUpdateStateAndPushFeedEvent() {
        NotificationEventMessage event = new NotificationEventMessage();
        event.setEventType(NotificationConstants.EventType.FEED_UNREAD);
        event.setRecipientUserId(6L);
        event.setOccurredAt(LocalDateTime.now());

        when(notificationUserStateMapper.selectOne(any())).thenReturn(buildState(6L, 2L, 1));

        NotificationMessageVO result = notificationService.consumeNotificationEvent(event);

        assertThat(result).isNull();
        verify(notificationMessageMapper, never()).insert(any(NotificationMessage.class));
        verify(notificationUserStateMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sseService).sendFeedUnread(6L, new NotificationSummaryVO(2L, true));
        verify(sseService, never()).sendNotification(eq(6L), any(), any());
    }

    @Test
    void markAllAsReadShouldResetUnreadCountAndPushSummary() {
        when(notificationUserStateMapper.selectOne(any())).thenReturn(buildState(11L, 0L, 1));

        NotificationSummaryVO summary = notificationService.markAllAsRead(11L);

        assertThat(summary).isEqualTo(new NotificationSummaryVO(0L, true));
        verify(notificationMessageMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(notificationUserStateMapper).update(eq(null), any(LambdaUpdateWrapper.class));
        verify(sseService).sendSummary(11L, new NotificationSummaryVO(0L, true));
    }

    private NotificationUserState buildState(Long userId, Long unreadCount, Integer feedUnread) {
        NotificationUserState state = new NotificationUserState();
        state.setUserId(userId);
        state.setUnreadNotificationCount(unreadCount);
        state.setFeedUnreadFlag(feedUnread);
        return state;
    }
}
