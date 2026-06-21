package com.game.community.notification.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;

public interface NotificationService {

    NotificationSummaryVO getSummary(Long userId);

    PageResult<NotificationMessageVO> listMessages(Long userId, Long page, Long size, Integer eventType);

    NotificationSummaryVO markAllAsRead(Long userId);

    NotificationSummaryVO markFeedRead(Long userId);

    NotificationMessageVO consumeNotificationEvent(NotificationEventMessage event);
}
