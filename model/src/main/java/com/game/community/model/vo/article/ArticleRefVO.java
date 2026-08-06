package com.game.community.model.vo.article;

import lombok.Data;

import java.io.Serializable;

/**
 * 转发帖引用的原帖简要信息
 */
@Data
public class ArticleRefVO implements Serializable {

    private String id;

    private String title;

    private String summary;

    private String coverUrl;

    private String videoUrl;

    /** 原帖发帖模式 */
    private Integer postType;

    private Long authorAccountId;

    private String username;

    private String avatar;
}
