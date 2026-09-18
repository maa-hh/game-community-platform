package com.game.community.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.notification.NotificationFeedEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

public interface NotificationFeedEventMapper extends BaseMapper<NotificationFeedEvent> {

    @Insert("""
            INSERT INTO t_notification_feed_event
            (user_id, event_id, feed_item_id, occurred_at, create_time)
            VALUES (#{event.userId}, #{event.eventId}, #{event.feedItemId},
                    #{event.occurredAt}, NOW())
            ON DUPLICATE KEY UPDATE id = id
            """)
    int insertIfAbsent(@Param("event") NotificationFeedEvent event);
}
