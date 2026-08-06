package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.game.community.model.enums.user.FieldAuditStatus;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户资料字段审核状态（与 t_user 1:1）
 */
@Data
@TableName("t_user_profile_audit")
public class UserProfileAudit implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private FieldAuditStatus usernameAuditStatus;

    private FieldAuditStatus signatureAuditStatus;

    private FieldAuditStatus avatarAuditStatus;

    private String pendingUsername;

    private String pendingSignature;

    private String pendingAvatar;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public String getPendingUsername() {
        return UserStrings.orEmpty(pendingUsername);
    }

    public String getPendingSignature() {
        return UserStrings.orEmpty(pendingSignature);
    }

    public String getPendingAvatar() {
        return UserStrings.orEmpty(pendingAvatar);
    }
}
