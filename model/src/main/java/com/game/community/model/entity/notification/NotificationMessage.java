package com.game.community.model.entity.notification;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_notification_message")
public class NotificationMessage implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private String aggregateKey;

    private Long userId;

    private Integer eventType;

    private Long actorUserId;

    private String actorUsername;

    private String actorAvatar;

    private Long articleId;

    private Long commentId;

    private Long replyId;

    private Long reportId;

    private Long targetUserId;

    private String previewText;

    private String resultText;

    private Integer routeType;

    private Integer readStatus;

    private LocalDateTime readTime;

    private LocalDateTime occurredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
