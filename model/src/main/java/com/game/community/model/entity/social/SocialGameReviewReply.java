package com.game.community.model.entity.social;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 游戏短评回复元数据；正文存 MongoDB，replyId 是唯一公开标识。 */
@Data
@TableName("t_social_game_review_reply")
public class SocialGameReviewReply implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String replyId;

    private String reviewId;

    private Long userId;

    private String username;

    private String avatar;

    private Long likeCount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
