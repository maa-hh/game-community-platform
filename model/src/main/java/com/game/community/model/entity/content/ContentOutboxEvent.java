package com.game.community.model.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内容领域可靠事件 Outbox。
 * 业务表事务提交后由独立投递器发送到 Kafka 或远程服务。
 */
@Data
@TableName("t_content_outbox")
public class ContentOutboxEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务幂等键。 */
    private String eventKey;

    /** ARTICLE_SEARCH_SYNC、NOTIFICATION_EVENT、MODERATION_TASK、SOCIAL_FEED。 */
    private String eventType;

    private String topic;

    private String messageKey;

    private String payload;

    /** 0待发送，1发送中，2已发送，3重试等待，4死信。 */
    private Integer status;

    private Integer retryCount;

    private LocalDateTime nextRetryTime;

    private String lockToken;

    private LocalDateTime lockTime;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
