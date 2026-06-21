package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_skin")
public class GameSkin implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String skinCode;

    private Long characterId;

    private String characterCode;

    private String name;

    private String icon;

    private Integer rarity;

    private Integer status;

    private Integer sortOrder;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
