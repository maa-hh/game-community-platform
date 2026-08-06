package com.game.community.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.notification.NotificationUserState;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface NotificationUserStateMapper extends BaseMapper<NotificationUserState> {

    @Insert("""
            INSERT IGNORE INTO t_notification_user_state
            (user_id, unread_notification_count, feed_unread_flag, last_feed_event_time,
             last_feed_read_time, update_time)
            VALUES (#{userId}, 0, 0, '1970-01-01 00:00:00', '1970-01-01 00:00:00', NOW())
            """)
    int insertIgnore(@Param("userId") Long userId);

    @Select("SELECT * FROM t_notification_user_state WHERE user_id = #{userId} LIMIT 1 FOR UPDATE")
    NotificationUserState selectByUserIdForUpdate(@Param("userId") Long userId);
}
