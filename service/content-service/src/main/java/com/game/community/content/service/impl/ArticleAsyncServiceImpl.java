package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.event.ArticleSearchSyncProducer;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.service.ArticleAsyncService;
import com.game.community.content.service.ArticleAuditService;
import com.game.community.content.service.ArticleContentService;
import com.game.community.feign.SocialFeignClient;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.audit.AuditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布任务的业务执行器。
 *
 * 异步边界统一由 TaskServiceImpl 控制，这里只负责在当前任务线程中
 * 完成审核、正文落库和文章状态回写，避免重复异步导致的事务边界分裂。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleAsyncServiceImpl implements ArticleAsyncService {

    private final ArticleAuditService articleAuditService;

    private final ArticleMapper articleMapper;

    private final ArticleContentService articleContentService;

    private final MinIOUtils minIOUtils;

    private final SocialFeignClient socialFeignClient;

    private final ArticleSearchSyncProducer articleSearchSyncProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditAndPublish(Long articleId, Long userId, ArticleDTO articleDTO, String coverUrl, List<String> imageUrls) {
        log.info("开始执行文章审核发布任务: articleId={}, userId={}", articleId, userId);
        List<String> auditImages = new ArrayList<>();
        if (StringUtils.hasText(coverUrl)) {
            auditImages.add(coverUrl);
        }
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (StringUtils.hasText(imageUrl) && !auditImages.contains(imageUrl)) {
                    auditImages.add(imageUrl);
                }
            }
        }

        AuditResult result = articleAuditService.auditArticle(articleId, articleDTO.getTitle(), articleDTO.getContent(), auditImages);
        if (result.isPass()) {
            LocalDateTime publishedTime = LocalDateTime.now();
            Map<String, String> paragraphs = normalizeParagraphs(articleDTO);
            articleContentService.saveContent(articleId, joinParagraphs(paragraphs), paragraphs, imageUrls, userId);
            articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                    .eq(Article::getId, articleId)
                    .set(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                    .set(Article::getAuditMessage, "审核通过")
                    .set(Article::getPublishedTime, publishedTime)
                    .set(Article::getUpdateTime, publishedTime));
            articleSearchSyncProducer.upsert(articleId);
            notifySocialFeed(userId, articleId, publishedTime);
            log.info("文章审核通过并发布: articleId={}", articleId);
            return;
        }

        articleMapper.update(null, new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, articleId)
                .set(Article::getStatus, ContentConstants.ArticleStatus.REJECTED)
                .set(Article::getAuditMessage, result.getReason())
                .set(Article::getUpdateTime, LocalDateTime.now()));
        articleSearchSyncProducer.delete(articleId);
        cleanupImages(auditImages);
        log.info("文章审核驳回: articleId={}, reason={}", articleId, result.getReason());
    }

    private void notifySocialFeed(Long userId, Long articleId, LocalDateTime publishedTime) {
        try {
            socialFeignClient.publishArticleToFollowers(userId, articleId, publishedTime.toString());
        } catch (RuntimeException e) {
            log.warn("推送文章到粉丝Feed失败: articleId={}, error={}", articleId, e.getMessage());
        }
    }

    private void cleanupImages(List<String> imageUrls) {
        if (imageUrls == null) {
            return;
        }
        for (String imageUrl : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            try {
                minIOUtils.deletePublicFileByUrl(imageUrl);
            } catch (Exception e) {
                log.warn("清理文章资源失败: url={}, error={}", imageUrl, e.getMessage());
            }
        }
    }

    private Map<String, String> normalizeParagraphs(ArticleDTO dto) {
        Map<String, String> result = new LinkedHashMap<>();
        if (dto.getContentParagraphs() != null && !dto.getContentParagraphs().isEmpty()) {
            dto.getContentParagraphs().forEach((key, value) -> {
                if (StringUtils.hasText(value)) {
                    result.put(StringUtils.hasText(key) ? key.trim() : "p" + (result.size() + 1), value.trim());
                }
            });
        }
        if (result.isEmpty() && StringUtils.hasText(dto.getContent())) {
            String[] paragraphs = dto.getContent().trim().split("\n{2,}");
            for (String paragraph : paragraphs) {
                if (StringUtils.hasText(paragraph)) {
                    result.put("p" + (result.size() + 1), paragraph.trim());
                }
            }
        }
        return result;
    }

    private String joinParagraphs(Map<String, String> paragraphs) {
        return String.join("\n\n", paragraphs.values());
    }
}
