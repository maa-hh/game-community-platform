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
 * 用户实体
 */
@Data
@TableName("t_user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对外展示和登录使用的账号号，内部关联仍使用主键 id。
     */
    private Long accountId;

    private String username;

    private String avatar;

    private String signature;

    private String phone;

    /**
     * 0-正常, 1-禁用/注销
     */
    private Integer status;

    /**
     * 0-普通用户, 1-管理员
     */
    private Integer type;

    private String gameAccount;

    /**
     * 0-无需审核/审核完成, 1-审核中
     */
    private Integer auditStatus;

    private LocalDateTime lastLoginTime;

    /**
     * 用户资料乐观锁版本号。
     */
    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
