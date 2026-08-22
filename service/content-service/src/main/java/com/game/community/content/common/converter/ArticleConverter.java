package com.game.community.content.common.converter;

import com.game.community.content.util.ArticleCategoryHelper;
import com.game.community.model.entity.article.Article;
import com.game.community.model.mongo.ArticleContent;
import com.game.community.model.vo.article.ArticleContentVO;
import com.game.community.model.vo.article.ArticleListVO;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 文章 Entity / Mongo → VO
 */
public final class ArticleConverter {

    private ArticleConverter() {
    }

    public static ArticleListVO toListVO(Article article) {
        if (article == null) {
            return null;
        }
        ArticleListVO vo = new ArticleListVO();
        vo.setId(article.getId());
        vo.setPublicId(article.getPublicId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setPostType(article.getPostType());
        vo.setRefArticleId(article.getRefArticleId());
        vo.setVideoUrl(article.getVideoUrl());
        vo.setCategoryId(article.getCategoryId());
        vo.setCategoryIds(ArticleCategoryHelper.resolveIds(article));
        vo.setStatus(article.getStatus());
        vo.setAuditMessage(article.getAuditMessage());
        vo.setScheduledPublishTime(article.getScheduledPublishTime());
        vo.setPublishedTime(article.getPublishedTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    public static List<ArticleListVO> toListVOs(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }
        return articles.stream().map(ArticleConverter::toListVO).filter(Objects::nonNull).toList();
    }

    public static ArticleContentVO toContentVO(ArticleContent content) {
        if (content == null) {
            return null;
        }
        ArticleContentVO vo = new ArticleContentVO();
        vo.setArticleId(content.getArticleId());
        vo.setContent(content.getContent());
        vo.setContentHtml(content.getContentHtml());
        vo.setContentParagraphs(content.getContentParagraphs());
        vo.setImageUrls(content.getImageUrls());
        vo.setCreateTime(content.getCreateTime());
        vo.setUpdateTime(content.getUpdateTime());
        return vo;
    }
}
