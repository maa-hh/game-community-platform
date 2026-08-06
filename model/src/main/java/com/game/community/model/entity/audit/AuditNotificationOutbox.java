package com.game.community.model.entity.audit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_audit_notification_outbox")
public class AuditNotificationOutbox implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String eventKey;
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
