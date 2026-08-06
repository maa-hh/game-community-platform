package com.game.community.model.entity.game;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_user_steam_bind")
public class UserSteamBind implements Serializable {

    @TableId(type = IdType.INPUT)
    private Long userId;

    private String steamId;

    private String personaName;

    private String avatarUrl;

    private String profileUrl;

    private Integer steamLevel;

    private Integer gameCount;

    private Integer libraryPublic;

    private LocalDateTime librarySyncedAt;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
