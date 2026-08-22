package com.game.community.content.service;

import com.game.community.model.mongo.ArticleContent;

/**
 * 文章内容服务接口
 */
public interface ArticleContentService {

    /**
     * 保存文章内容到MongoDB
     *
     * @param articleId 文章ID
     * @param content   正文纯文本
     * @param contentHtml 富文本 HTML（图文正文或视频介绍）
     * @param contentParagraphs 正文纯文本段落
     * @param imageUrls 图片URL列表
     * @param userId    用户ID
     * @return ArticleContent
     */
    ArticleContent saveContent(Long articleId, String content, String contentHtml,
                               java.util.Map<String, String> contentParagraphs,
                               java.util.List<String> imageUrls, Long userId);

    /**
     * 根据文章ID获取内容
     *
     * @param articleId 文章ID
     * @return ArticleContent
     */
    ArticleContent getByArticleId(Long articleId);

    /**
     * 根据文章ID删除内容
     *
     * @param articleId 文章ID
     */
    void deleteByArticleId(Long articleId);
}
