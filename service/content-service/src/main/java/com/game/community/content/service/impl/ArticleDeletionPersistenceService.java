package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.content.event.ArticleNotificationEventProducer;
import com.game.community.content.event.ArticleSearchSyncProducer;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.service.ChunkUploadService;
import com.game.community.content.service.ContentOutboxService;
import com.game.community.content.service.TaskService;
import com.game.community.content.util.ArticleMediaHelper;
import com.game.community.model.entity.article.Article;
import com.game.community.model.mongo.ArticleContent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 先可靠提交文章删除状态，再异步清理 Mongo、MinIO 和上传会话。 */
@Service
@RequiredArgsConstructor
public class ArticleDeletionPersistenceService {

    private final ArticleMapper articleMapper;
    private final TaskService taskService;
    private final ArticleSearchSyncProducer articleSearchSyncProducer;
    private final ContentOutboxService contentOutboxService;
    private final ArticleNotificationEventProducer articleNotificationEventProducer;
    private final ArticleMediaHelper articleMediaHelper;

    @Transactional(rollbackFor = Exception.class)
    public boolean markDeleted(Article article, ArticleContent content) {
        int updated = articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, article.getId())
                .eq(Article::getDeleted, 0)
                .set(Article::getDeleted, 1)
                .set(Article::getStatus, ContentConstants.ArticleStatus.OFFLINE)
                .set(Article::getAuditMessage, "已删除")
                .set(Article::getUpdateTime, java.time.LocalDateTime.now()));
        if (updated == 0) {
            return false;
        }
        taskService.cancelTasksByBusinessId(article.getId(), ContentConstants.TaskType.ARTICLE_PUBLISH);
        articleSearchSyncProducer.delete(article.getId());

        List<String> refs = new ArrayList<>();
        if (article.getCoverUrl() != null) {
            refs.add(article.getCoverUrl());
        }
        if (article.getVideoUrl() != null) {
            refs.add(article.getVideoUrl());
        }
        if (content != null && content.getImageUrls() != null) {
            refs.addAll(content.getImageUrls());
        }
        articleMediaHelper.removeFromBlacklist(refs);
        contentOutboxService.enqueue(
                "article-cleanup:" + article.getId() + ":" + UUID.randomUUID(),
                "ARTICLE_CLEANUP", null, String.valueOf(article.getId()),
                Map.of("articleId", article.getId(), "userId", article.getUserId(), "refs", refs));
        articleNotificationEventProducer.publishProfileInvalidation(article.getUserId(),
                NotificationConstants.ProfileDataDomain.POSTS);
        return true;
    }
}
