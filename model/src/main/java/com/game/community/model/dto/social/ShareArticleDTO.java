package com.game.community.model.dto.social;

import lombok.Data;

import java.io.Serializable;

/**
 * 文章分享计数请求
 */
@Data
public class ShareArticleDTO implements Serializable {

    /**
     * 分享渠道：link / repost / external
     */
    private String channel;
}
