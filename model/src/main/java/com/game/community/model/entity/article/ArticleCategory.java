package com.game.community.model.entity.article;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 文章与分类的规范化关系表；category_ids JSON 仅作为兼容快照。 */
@Data
@TableName("t_article_category")
public class ArticleCategory implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long categoryId;

    private LocalDateTime createTime;
}
