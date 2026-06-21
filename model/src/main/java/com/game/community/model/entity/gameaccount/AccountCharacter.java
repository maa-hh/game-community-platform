package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_account_character")
public class AccountCharacter implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long gameAccountId;

    private Long characterId;

    private String characterCode;

    private Integer level;

    private Integer exp;

    private Integer breakthroughLevel;

    private LocalDateTime obtainTime;

    private Integer status;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
