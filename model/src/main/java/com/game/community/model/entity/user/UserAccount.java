package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.game.community.model.enums.user.AccountType;
import com.game.community.model.enums.user.RegisterSource;
import com.game.community.model.enums.user.UserAccountStatus;
import com.game.community.model.enums.user.UserStrings;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户账户实体（状态与生命周期，与用户资料表 t_user 分离）
 */
@Data
@TableName("t_user_account")
public class UserAccount implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private UserAccountStatus status;

    private AccountType type;

    /** 封禁截止时间；未封禁或已解封时为空时间语义由业务判断 */
    private LocalDateTime banUntil;

    private String banReason;

    private LocalDateTime cancelAt;

    private RegisterSource registerSource;

    private LocalDateTime lastLoginTime;

    private String lastLoginIp;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;

    public String getBanReason() {
        return UserStrings.orEmpty(banReason);
    }

    public String getLastLoginIp() {
        return UserStrings.orEmpty(lastLoginIp);
    }
}
