package com.game.community.model.entity.gameaccount;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_game_character")
public class GameCharacter implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String characterCode;

    private String name;

    private String title;

    private String icon;

    private Integer rarity;

    private Integer elementType;

    private Integer characterType;

    private Integer status;

    private Integer sortOrder;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
