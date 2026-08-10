package com.game.community.model.vo.game;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class GameReviewVO implements Serializable {

    /** 对外短评标识，不使用数据库自增主键。 */
    private String reviewId;

    private Long appId;

    /** 对外展示账号 ID */
    private Long accountId;

    private String username;

    private String avatar;

    private Integer score;

    private String content;

    private Long likeCount;

    private Long replyCount;

    private Boolean liked;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
