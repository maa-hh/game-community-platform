package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户认证实体
 * <p>
 * 职责：密码存储、登录安全（失败计数、锁定）。
 */
@Data
@TableName("t_user_auth")
public class UserAuth implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String password;

    private String salt;

    /** 连续登录失败次数，成功后清零 */
    private Integer failCount;

    /** 账号锁定截止时间，NULL=未锁定 */
    private LocalDateTime lockUntil;

    /** 最后修改密码时间 */
    private LocalDateTime lastPasswordChange;

    /** 认证数据乐观锁版本号 */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
