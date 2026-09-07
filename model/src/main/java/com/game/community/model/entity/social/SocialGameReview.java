package com.game.community.model.entity.social;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 游戏短评的社交元数据，正文仍保存在 MongoDB。 */
@Data
@TableName("t_social_game_review")
public class SocialGameReview implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reviewId;

    private Long appId;

    private Long userId;

    private Long likeCount;

    private Long replyCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
