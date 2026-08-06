package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GameReviewVO implements Serializable {

    private Long appId;

    /** 对外展示账号 ID */
    private Long accountId;

    private String username;

    private String avatar;

    private Integer score;

    private String content;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
