package com.game.community.model.entity.social;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 社交服务可靠事件 Outbox。业务数据与事件在同一个 MySQL 事务中提交。
 */
@Data
@TableName("t_social_outbox")
public class SocialOutboxEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventKey;
    private String eventType;
    private String topic;
    private String messageKey;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private LocalDateTime nextRetryTime;
    private String lockToken;
    private LocalDateTime lockTime;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
