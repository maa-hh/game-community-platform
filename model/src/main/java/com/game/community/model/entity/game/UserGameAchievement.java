package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 用户某款游戏的成就状态快照。只有 current=1 的版本对外展示。 */
@Data
@TableName("t_user_game_achievement")
public class UserGameAchievement implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long appId;
    private String apiName;
    private Integer unlocked;
    private LocalDateTime unlockTime;
    private String syncId;
    private Integer isCurrent;
    private LocalDateTime syncedAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
