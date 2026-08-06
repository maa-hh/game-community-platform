package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户资料实体（纯身份属性，与账号状态 t_user_account 分离）
 * <p>
 * 职责：昵称、头像、签名、邮箱、Steam账号等已通过的用户资料。
 * 账号状态见 t_user_account；字段审核状态见 t_user_profile_audit。
 */
@Data
@TableName("t_user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 对外展示和登录使用的账号号，内部关联仍使用主键 id */
    private Long accountId;

    private String username;

    private String avatar;

    private String signature;

    private String email;

    /** Steam账号ID（仅展示，无权操作Steam内部资产） */
    private String steamAccount;

    /** 用户资料乐观锁版本号 */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public String getAvatar() {
        return UserStrings.orEmpty(avatar);
    }

    public String getSignature() {
        return UserStrings.orEmpty(signature);
    }

    public String getSteamAccount() {
        return UserStrings.orEmpty(steamAccount);
    }
}
