package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.game.community.model.enums.user.AuditFieldType;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 审核异步队列拒绝记录
 */
@Data
@TableName("t_user_audit_reject_log")
public class UserAuditRejectLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long accountId;

    private Long taskId;

    private AuditFieldType taskType;

    private String requestData;

    private String rejectReason;

    private String poolSnapshot;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public String getRequestData() {
        return UserStrings.orEmpty(requestData);
    }

    public String getRejectReason() {
        return UserStrings.orEmpty(rejectReason);
    }

    public String getPoolSnapshot() {
        return UserStrings.orEmpty(poolSnapshot);
    }
}
