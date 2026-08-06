package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.service.ArticleAsyncService;
import com.game.community.content.service.ArticleAuditService;
import com.game.community.content.service.ArticleContentService;
import com.game.community.content.util.ArticleMediaHelper;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.utils.audit.AuditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 发布任务执行器：审核 → 私有媒体提升公共桶 → 状态回写。
 * 若作者已取消上架/删除，则中止发布。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleAsyncServiceImpl implements ArticleAsyncService {

    private final ArticleAuditService articleAuditService;

    private final ArticleMapper articleMapper;

    private final ArticleContentService articleContentService;

    private final ArticleMediaHelper articleMediaHelper;

    private final ArticlePublishPersistenceService articlePublishPersistenceService;

    @Override
    public void auditAndPublish(Long articleId, Long userId, ArticleDTO articleDTO, String coverUrl, List<String> imageUrls) {
        log.info("开始执行文章审核发布任务: articleId={}, userId={}", articleId, userId);
        if (shouldAbortPublish(articleId)) {
            log.info("文章已取消上架或删除，跳过审核发布: articleId={}", articleId);
            return;
        }

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
        if (shouldAbortPublish(articleId)) {
            log.info("审核完成后文章已取消，放弃发布: articleId={}", articleId);
            return;
        }

        if (result.isHumanReview()) {
            LocalDateTime snapshotAt = LocalDateTime.now();
            articlePublishPersistenceService.enterManualReview(articleId, userId, articleDTO,
                    result.getReason(), snapshotAt);
            log.info("文章进入人工审核: articleId={}", articleId);
            return;
        }

        if (result.isPass()) {
            LocalDateTime publishedTime = LocalDateTime.now();
            Map<String, String> paragraphs = normalizeParagraphs(articleDTO);
            ArticleMediaHelper.PromotedGallery gallery =
                    articleMediaHelper.promoteGallery(coverUrl, imageUrls, "content");
            String publicCover = gallery.coverUrl();
            List<String> publicImages = gallery.imageUrls();
            String publicVideo = articleMediaHelper.promoteToPublic(articleDTO.getVideoUrl(), "video");

            if (shouldAbortPublish(articleId)) {
                log.info("媒体提升后文章已取消，放弃写发布态: articleId={}", articleId);
                return;
            }

            boolean updated = articlePublishPersistenceService.publish(articleId, userId,
                    joinParagraphs(paragraphs), paragraphs, publicImages, publicCover, publicVideo, publishedTime);
            if (!updated) {
                log.info("文章状态已变更，未写入发布: articleId={}", articleId);
                return;
            }
            articleMediaHelper.deletePendingRefs(concatRefs(coverUrl, imageUrls, articleDTO.getVideoUrl()));
            log.info("文章审核通过并发布: articleId={}", articleId);
            return;
        }

        boolean rejected = articlePublishPersistenceService.reject(articleId, userId, result.getReason());
        if (rejected) {
            articleMediaHelper.deleteRefs(auditImages);
            articleMediaHelper.deletePendingRefs(List.of(articleDTO.getVideoUrl()));
        }
        log.info("文章审核驳回: articleId={}, reason={}", articleId, result.getReason());
    }

    @Override
    public void publishAfterManualApproval(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            return;
        }
        if (!Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING)) {
            throw new BusinessException("文章状态不可人工通过");
        }
        var content = articleContentService.getByArticleId(articleId);
        ArticleDTO articleDTO = new ArticleDTO();
        articleDTO.setTitle(article.getTitle());
        articleDTO.setSummary(article.getSummary());
        articleDTO.setVideoUrl(article.getVideoUrl());
        if (content != null) {
            articleDTO.setContent(content.getContent());
            articleDTO.setContentParagraphs(content.getContentParagraphs());
        }
        List<String> imageUrls = content == null || content.getImageUrls() == null ? List.of() : content.getImageUrls();
        publishApprovedArticle(articleId, article.getUserId(), articleDTO, article.getCoverUrl(), imageUrls);
    }

    @Override
    public void rejectAfterManualApproval(Long articleId, String reason) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.REJECTED)) {
            return;
        }
        if (!Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING)) {
            throw new BusinessException("文章状态不可人工驳回");
        }
        var content = articleContentService.getByArticleId(articleId);
        List<String> auditImages = new ArrayList<>();
        if (StringUtils.hasText(article.getCoverUrl())) {
            auditImages.add(article.getCoverUrl());
        }
        if (content != null && content.getImageUrls() != null) {
            auditImages.addAll(content.getImageUrls());
        }
        boolean rejected = articlePublishPersistenceService.reject(articleId, article.getUserId(),
                StringUtils.hasText(reason) ? reason : "人工审核未通过");
        if (rejected) {
            articleMediaHelper.deleteRefs(auditImages);
            articleMediaHelper.deletePendingRefs(List.of(article.getVideoUrl()));
        }
    }

    private void publishApprovedArticle(Long articleId, Long userId, ArticleDTO articleDTO,
                                        String coverUrl, List<String> imageUrls) {
        LocalDateTime publishedTime = LocalDateTime.now();
        Map<String, String> paragraphs = normalizeParagraphs(articleDTO);
        ArticleMediaHelper.PromotedGallery gallery =
                articleMediaHelper.promoteGallery(coverUrl, imageUrls, "content");
        String publicCover = gallery.coverUrl();
        List<String> publicImages = gallery.imageUrls();
        String publicVideo = articleMediaHelper.promoteToPublic(articleDTO.getVideoUrl(), "video");
        boolean updated = articlePublishPersistenceService.publishAfterManualApproval(articleId, userId,
                joinParagraphs(paragraphs), paragraphs, publicImages, publicCover, publicVideo, publishedTime);
        if (!updated) {
            throw new BusinessException("文章状态已变更，发布失败");
        }
        articleMediaHelper.deletePendingRefs(concatRefs(coverUrl, imageUrls, articleDTO.getVideoUrl()));
    }

    private boolean shouldAbortPublish(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            return true;
        }
        if (Objects.equals(article.getDeleted(), 1)) {
            return true;
        }
        return !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING);
    }

    private List<String> concatRefs(String coverUrl, List<String> imageUrls, String videoUrl) {
        List<String> refs = new ArrayList<>();
        if (StringUtils.hasText(coverUrl)) {
            refs.add(coverUrl);
        }
        if (imageUrls != null) {
            refs.addAll(imageUrls);
        }
        if (StringUtils.hasText(videoUrl)) {
            refs.add(videoUrl);
        }
        return refs;
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
