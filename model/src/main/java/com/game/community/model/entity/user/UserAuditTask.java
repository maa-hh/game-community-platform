package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.AuditMode;
import com.game.community.model.enums.user.AuditTaskStatus;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户字段审核任务
 */
@Data
@TableName("t_user_audit_task")
public class UserAuditTask implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private AuditFieldType taskType;

    private AuditTaskStatus status;

    private String pendingContent;

    private String payload;

    private AuditMode auditMode;

    private Integer score;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public String getPendingContent() {
        return UserStrings.orEmpty(pendingContent);
    }

    public String getPayload() {
        return UserStrings.orEmpty(payload);
    }

    public String getErrorMessage() {
        return UserStrings.orEmpty(errorMessage);
    }
}
