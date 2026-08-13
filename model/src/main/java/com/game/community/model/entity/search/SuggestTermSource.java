package com.game.community.model.entity.search;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("t_suggest_term_source")
public class SuggestTermSource implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long termId;

    private String sourceType;

    /** 0 表示游戏、上传等非帖子来源，避免数据库 NULL。 */
    private Long sourceArticleId;

    private LocalDateTime createdAt;
}
