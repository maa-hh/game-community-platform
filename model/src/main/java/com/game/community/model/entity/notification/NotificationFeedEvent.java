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
@TableName("t_notification_feed_event")
public class NotificationFeedEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String eventId;

    private Long feedItemId;

    private LocalDateTime occurredAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
