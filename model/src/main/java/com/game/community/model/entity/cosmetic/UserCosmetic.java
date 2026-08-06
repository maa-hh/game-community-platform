package com.game.community.model.entity.cosmetic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_cosmetic")
public class UserCosmetic implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String cosmeticCode;

    private Integer quantity;

    private String sourceType;

    private String sourceRef;

    private LocalDateTime acquiredAt;

    private LocalDateTime expireAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
