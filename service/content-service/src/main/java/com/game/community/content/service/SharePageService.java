package com.game.community.content.service;

/**
 * 外链分享 OG 落地页
 */
public interface SharePageService {

    /**
     * 渲染分享页 HTML（仅已发布且未删除的帖子）
     */
    String renderSharePage(String publicId);
}
