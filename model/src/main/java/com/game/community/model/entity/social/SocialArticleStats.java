package com.game.community.model.entity.social;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_social_article_stats")
public class SocialArticleStats implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long likeCount;

    private Long commentCount;

    private Long commentLikeCount;

    private Long replyCount;

    private Long replyLikeCount;

    private Long viewCount;

    private Long favoriteCount;

    private Long shareCount;

    @Version
    private Integer version;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
