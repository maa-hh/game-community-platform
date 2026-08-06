package com.game.community.model.entity.user;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 账号ID号池实体
 * <p>
 * 号池机制：预生成所有可用ID，注册时CAS抢占。
 * 初始5位(10000-99999)，耗尽后扩容6位(100000-999999)，以此类推。
 */
@Data
@TableName("t_account_id_pool")
public class AccountIdPool implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 可用的账号ID */
    private Long accountId;

    /** 位数（5/6/7...） */
    private Integer digitCount;

    /** 0=可用, 1=已占用 */
    private Integer status;

    /** 占用该ID的用户ID */
    private Long userId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
