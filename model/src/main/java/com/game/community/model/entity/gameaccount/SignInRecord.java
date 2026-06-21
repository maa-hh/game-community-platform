package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_sign_in_record")
public class SignInRecord implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gameAccountId;

    @TableField("month_key")
    private String yearMonth;

    private Long signBits;

    private Integer signCount;

    private Integer consecutiveDays;

    private LocalDateTime lastSignInDate;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
