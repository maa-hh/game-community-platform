package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.event.ArticleNotificationEventProducer;
import com.game.community.content.event.ArticleSearchSyncProducer;
import com.game.community.content.event.ArticleSocialFeedProducer;
import com.game.community.content.event.ModerationTaskProducer;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.service.ArticleContentService;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 只负责发布状态和 Outbox 的数据库事务；媒体、AI、远程调用均在事务外执行。 */
@Service
@RequiredArgsConstructor
public class ArticlePublishPersistenceService {

    private final ArticleMapper articleMapper;
    private final ArticleContentService articleContentService;
    private final ArticleSearchSyncProducer articleSearchSyncProducer;
    private final ArticleSocialFeedProducer articleSocialFeedProducer;
    private final ArticleNotificationEventProducer articleNotificationEventProducer;
    private final ModerationTaskProducer moderationTaskProducer;

    @Transactional(rollbackFor = Exception.class)
    public boolean publish(Long articleId, Long userId, String content, Map<String, String> paragraphs,
                           List<String> publicImages, String publicCover, String publicVideo,
                           LocalDateTime publishedTime) {
        articleContentService.saveContent(articleId, content, paragraphs, publicImages, userId);
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PENDING)
                .set(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                .set(Article::getAuditMessage, "审核通过")
                .set(Article::getCoverUrl, publicCover)
                .set(Article::getVideoUrl, publicVideo)
                .set(Article::getPublishedTime, publishedTime)
                .set(Article::getUpdateTime, publishedTime));
        if (updated == 0) {
            return false;
        }
        articleSearchSyncProducer.upsert(articleId);
        articleSocialFeedProducer.publish(userId, articleId, publishedTime);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public void enterManualReview(Long articleId, Long userId, ArticleDTO dto, String reason,
                                  LocalDateTime snapshotAt) {
        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PENDING)
                .set(Article::getAuditMessage, StringUtils.hasText(reason) ? reason : "待人工审核")
                .set(Article::getUpdateTime, snapshotAt));
        moderationTaskProducer.publishArticleAudit(articleId, userId, dto.getTitle(), dto.getContent(),
                reason, snapshotAt);
        articleNotificationEventProducer.publishArticleAuditHumanReview(userId, articleId, reason);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean reject(Long articleId, Long userId, String reason) {
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PENDING)
                .set(Article::getStatus, ContentConstants.ArticleStatus.REJECTED)
                .set(Article::getAuditMessage, reason)
                .set(Article::getUpdateTime, LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }
        articleSearchSyncProducer.delete(articleId);
        articleNotificationEventProducer.publishArticleAuditRejected(userId, articleId, reason);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean publishAfterManualApproval(Long articleId, Long userId, String content,
                                              Map<String, String> paragraphs, List<String> publicImages,
                                              String publicCover, String publicVideo,
                                              LocalDateTime publishedTime) {
        articleContentService.saveContent(articleId, content, paragraphs, publicImages, userId);
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PENDING)
                .set(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                .set(Article::getAuditMessage, "人工审核通过")
                .set(Article::getCoverUrl, publicCover)
                .set(Article::getVideoUrl, publicVideo)
                .set(Article::getPublishedTime, publishedTime)
                .set(Article::getUpdateTime, publishedTime));
        if (updated == 0) {
            return false;
        }
        articleSearchSyncProducer.upsert(articleId);
        articleSocialFeedProducer.publish(userId, articleId, publishedTime);
        return true;
    }
}
