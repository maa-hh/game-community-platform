package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_item")
public class GameItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String itemCode;

    private String name;

    private Integer itemType;

    private Integer rarity;

    private String icon;

    private Integer maxStackCount;

    private Integer status;

    private Integer sortOrder;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
