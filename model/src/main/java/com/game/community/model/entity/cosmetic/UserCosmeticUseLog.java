package com.game.community.model.entity.cosmetic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_cosmetic_use_log")
public class UserCosmeticUseLog implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String cosmeticCode;

    private LocalDateTime useTime;

    private String bizRef;

    private String payloadJson;
}
