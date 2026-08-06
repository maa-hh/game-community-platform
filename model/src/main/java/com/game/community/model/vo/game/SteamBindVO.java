package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class SteamBindVO implements Serializable {

    /** 对外展示账号 ID */
    private Long accountId;

    private String steamId;

    private String personaName;

    private String avatarUrl;

    private String profileUrl;

    private Integer steamLevel;

    private Integer gameCount;

    private Boolean libraryPublic;

    private LocalDateTime librarySyncedAt;

    private LocalDateTime bindTime;
}
