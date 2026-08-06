package com.game.community.model.entity.cosmetic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_active_effect")
public class UserActiveEffect implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String effectCode;

    private String sourceCosmeticCode;

    private LocalDateTime startAt;

    private LocalDateTime expireAt;

    private String payloadJson;
}
