package com.game.community.notification.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.model.vo.notification.NotificationCategorySummaryVO;
import com.game.community.model.vo.notification.NotificationMessageVO;
import com.game.community.model.vo.notification.NotificationSummaryVO;

import java.util.List;

public interface NotificationService {

    NotificationSummaryVO getSummary(Long userId);

    List<NotificationCategorySummaryVO> getCategorySummaries(Long userId);

    PageResult<NotificationMessageVO> listMessages(Long userId, Long page, Long size, Integer eventType);

    PageResult<NotificationMessageVO> listMessagesByCategory(Long userId, Long page, Long size, String category);

    List<NotificationMessageVO> listMessagesAfterId(Long userId, Long afterId, int limit);

    NotificationSummaryVO markAllAsRead(Long userId);

    NotificationSummaryVO markCategoryAsRead(Long userId, String category);

    NotificationSummaryVO markFeedRead(Long userId);

    NotificationMessageVO consumeNotificationEvent(NotificationEventMessage event);
}
