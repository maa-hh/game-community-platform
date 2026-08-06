package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_steam_game")
public class UserSteamGame implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long appId;

    private String name;

    private String iconUrl;

    private Integer playtimeForever;

    private Integer playtimeTwoWeeks;

    private Integer achievementUnlocked;

    private Integer achievementTotal;

    private LocalDateTime lastPlayedAt;

    private LocalDateTime syncedAt;
}
