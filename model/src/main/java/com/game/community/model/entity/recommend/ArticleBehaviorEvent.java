package com.game.community.model.entity.recommend;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_article_behavior_event")
public class ArticleBehaviorEvent implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventId;

    private Long articleId;

    private Long likeDelta;

    private Long commentDelta;

    private Long danmakuDelta;

    private Long viewDelta;

    private Long favoriteDelta;

    private Long shareDelta;

    private Long commentLikeDelta;

    private Long replyLikeDelta;

    private Double scoreDelta;

    private LocalDateTime eventTime;

    private Long eventTimeMs;
}
