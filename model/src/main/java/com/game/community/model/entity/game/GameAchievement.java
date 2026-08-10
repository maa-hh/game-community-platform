package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Steam 公共成就定义及全球获取率缓存。 */
@Data
@TableName("t_game_achievement")
public class GameAchievement implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appId;
    private String apiName;
    private String name;
    private String description;
    private String iconUrl;
    private Double globalPercent;
    private LocalDateTime syncedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
