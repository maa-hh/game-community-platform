package com.game.community.notification.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.community.model.entity.notification.NotificationMessage;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;

public interface NotificationMessageMapper extends BaseMapper<NotificationMessage> {

    @Insert("""
            INSERT INTO t_notification_message
            (event_id, aggregate_key, user_id, event_type, actor_user_id, actor_username, actor_avatar,
             article_id, comment_id, reply_id, danmaku_id, video_public_id, game_app_id, game_review_id, game_review_reply_id, report_id, target_user_id, preview_text, result_text,
             route_type, read_status, read_time, occurred_at, create_time)
            VALUES
            (#{message.eventId}, #{message.aggregateKey}, #{message.userId}, #{message.eventType},
             #{message.actorUserId}, #{message.actorUsername}, #{message.actorAvatar}, #{message.articleId},
             #{message.commentId}, #{message.replyId}, #{message.danmakuId}, #{message.videoPublicId}, #{message.gameAppId}, #{message.gameReviewId}, #{message.gameReviewReplyId},
             #{message.reportId}, #{message.targetUserId},
             #{message.previewText}, #{message.resultText}, #{message.routeType}, #{message.readStatus},
             #{message.readTime}, #{message.occurredAt}, #{message.createTime})
            ON DUPLICATE KEY UPDATE id = id
            """)
    @Options(useGeneratedKeys = true, keyProperty = "message.id", keyColumn = "id")
    int insertIfAbsent(@Param("message") NotificationMessage message);
}
