package com.game.community.model.entity.social;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_social_feed_item")
public class SocialFeedItem implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long authorId;

    private Long articleId;

    private LocalDateTime publishedTime;

    private Integer sourceType;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
