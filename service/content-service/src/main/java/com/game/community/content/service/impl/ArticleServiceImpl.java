package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.content.common.converter.ArticleConverter;
import com.game.community.content.event.ArticleSearchSyncProducer;
import com.game.community.content.event.ArticleSocialFeedProducer;
import com.game.community.content.event.ArticleNotificationEventProducer;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.util.ArticleCategoryHelper;
import com.game.community.content.util.ArticleContentCompat;
import com.game.community.content.service.ArticleCategoryService;
import com.game.community.content.service.ArticleGameService;
import com.game.community.content.service.ArticleAuthorEnricher;
import com.game.community.content.service.ArticleContentService;
import com.game.community.content.service.ArticleService;
import com.game.community.content.service.ChunkUploadService;
import com.game.community.content.service.TaskService;
import com.game.community.content.util.ArticleMediaHelper;
import com.game.community.content.util.ArticleRichTextSanitizer;
import com.game.community.content.mapper.ArticleAuditMapper;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.article.ArticleAudit;
import com.game.community.model.entity.task.Task;
import com.game.community.model.mongo.ArticleContent;
import com.game.community.model.enums.user.AccountType;
import com.game.community.model.vo.article.ArticleContentVO;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.ArticleProgressVO;
import com.game.community.model.vo.article.ArticleRefVO;
import com.game.community.model.vo.file.ChunkUploadStatusVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文章服务实现：
 * 草稿直接落 MySQL + Mongo；
 * 发布请求先落库为待审核，再由任务系统异步审核与发布。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final ArticleContentService articleContentService;

    private final TaskService taskService;

    private final RedisUtils redisUtils;

    private final ArticleSearchSyncProducer articleSearchSyncProducer;

    private final ArticleSocialFeedProducer articleSocialFeedProducer;

    private final ArticleMediaHelper articleMediaHelper;

    private final ChunkUploadService chunkUploadService;

    private final ArticleAuditMapper articleAuditMapper;

    private final UserFeignClient userFeignClient;

    private final ArticleCategoryService articleCategoryService;

    private final ArticleGameService articleGameService;

    private final ArticleAuthorEnricher articleAuthorEnricher;

    private final ArticleDeletionPersistenceService articleDeletionPersistenceService;

    private final ArticleNotificationEventProducer articleNotificationEventProducer;

    private static final int MAX_CONTENT_LENGTH = 3000;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveArticle(ArticleDTO dto, Long userId) {
        validatePublishTime(dto.getScheduledPublishTime());
        int postType = normalizePostType(dto.getPostType());
        boolean isUpdate = dto.getId() != null;
        Article existingArticle = isUpdate ? requireOwnedArticle(dto.getId(), userId) : null;
        boolean wasPublished = existingArticle != null
                && Objects.equals(existingArticle.getStatus(), ContentConstants.ArticleStatus.PUBLISHED);
        ArticleContent existingContent = isUpdate ? articleContentService.getByArticleId(dto.getId()) : null;
        List<String> oldMediaRefs = collectMediaRefs(existingArticle, existingContent);
        Article refArticle = resolveRefArticleIfRepost(postType, dto.getRefArticleId());
        List<Long> categoryIds = resolveCategoryIds(dto, postType, refArticle);
        List<String> articleImages = normalizeArticleImages(dto.getImageUrls(), dto.getCoverUrl(), postType);
        String coverUrl = resolveCoverUrl(dto.getCoverUrl(), articleImages, postType, refArticle);
        String videoUrl = StringUtils.hasText(dto.getVideoUrl()) ? dto.getVideoUrl().trim() : null;
        String contentHtml = (postType == ContentConstants.PostType.IMAGE_TEXT
                || postType == ContentConstants.PostType.VIDEO)
                ? ArticleRichTextSanitizer.sanitize(dto.getContentHtml()) : null;
        Map<String, String> contentParagraphs = normalizeParagraphs(dto);
        String contentText = joinParagraphs(contentParagraphs);
        if (postType == ContentConstants.PostType.IMAGE_TEXT) {
            ArticleContentCompat.NormalizedImageText normalized =
                    ArticleContentCompat.normalizeImageTextSave(
                            contentText, contentParagraphs, articleImages, coverUrl);
            contentText = normalized.content();
            contentParagraphs = normalized.contentParagraphs();
            articleImages = normalized.imageUrls();
            coverUrl = normalized.coverUrl();
        }
        if (postType == ContentConstants.PostType.REPOST && !StringUtils.hasText(contentText)) {
            contentText = ContentConstants.Repost.DEFAULT_COMMENT;
            contentParagraphs = new LinkedHashMap<>();
            contentParagraphs.put("p1", contentText);
        }
        boolean draft = Objects.equals(dto.getStatus(), ContentConstants.ArticleStatus.DRAFT);
        // 保存草稿不进入审核，也允许正文/视频尚未完成；提交审核时再做完整校验。
        if (!draft) {
            validateContent(contentText, postType);
        }

        Article article = isUpdate ? existingArticle : new Article();
        if (!isUpdate) {
            article.setUserId(userId);
            article.setPublicId(newPublicId());
            article.setCreateTime(LocalDateTime.now());
            article.setDeleted(0);
        }

        article.setTitle(dto.getTitle().trim());
        article.setSummary(StringUtils.hasText(dto.getSummary()) ? dto.getSummary().trim() : buildSummary(contentText));
        article.setCoverUrl(coverUrl);
        article.setPostType(postType);
        article.setRefArticleId(postType == ContentConstants.PostType.REPOST
                ? refArticle.getPublicId() : null);
        article.setVideoUrl(videoUrl);
        if (postType == ContentConstants.PostType.REPOST && dto.getCategoryId() == null
                && (dto.getCategoryIds() == null || dto.getCategoryIds().isEmpty()) && refArticle != null) {
            article.setCategoryId(refArticle.getCategoryId());
        } else {
            article.setCategoryId(categoryIds.get(0));
        }
        article.setCategoryIds(categoryIds);
        article.setScheduledPublishTime(dto.getScheduledPublishTime());
        article.setUpdateTime(LocalDateTime.now());

        if (draft) {
            article.setStatus(ContentConstants.ArticleStatus.DRAFT);
            article.setAuditMessage(null);
            article.setPublishedTime(null);
            saveOrUpdate(article);
            articleCategoryService.replaceCategoryRelations(article.getId(), categoryIds);
            articleContentService.saveContent(article.getId(), contentText, contentHtml,
                    contentParagraphs, articleImages, userId);
            articleGameService.saveArticleGames(article.getId(), dto.getGameAppIds());
            articleSearchSyncProducer.delete(article.getId());
            if (wasPublished) {
                articleSocialFeedProducer.remove(article.getId());
            }
            articleNotificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.POSTS);
            cleanupReplacedMedia(oldMediaRefs, collectMediaRefs(article, articleContentService.getByArticleId(article.getId())));
            log.info("文章草稿保存成功: articleId={}, userId={}", article.getId(), userId);
            return article.getId();
        }

        if (postType == ContentConstants.PostType.VIDEO && !StringUtils.hasText(videoUrl)) {
            throw new BusinessException("视频模式下请先完成视频上传");
        }

        article.setStatus(ContentConstants.ArticleStatus.PENDING);
        article.setAuditMessage("审核中");
        article.setPublishedTime(null);
        saveOrUpdate(article);
        Long articleId = article.getId();
        articleCategoryService.replaceCategoryRelations(articleId, categoryIds);
        articleContentService.saveContent(articleId, contentText, contentHtml,
                contentParagraphs, articleImages, userId);
        articleGameService.saveArticleGames(articleId, dto.getGameAppIds());
        // 已发布文章重新提交审核时，必须先撤出公开检索和动态，否则审核期间仍会被用户看到。
        articleSearchSyncProducer.delete(articleId);
        if (wasPublished) {
            articleSocialFeedProducer.remove(articleId);
        }
        cleanupReplacedMedia(oldMediaRefs, collectMediaRefs(article, articleContentService.getByArticleId(articleId)));
        articleNotificationEventProducer.publishProfileInvalidation(userId,
                NotificationConstants.ProfileDataDomain.POSTS);

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("articleId", articleId);
        taskParam.put("userId", userId);
        taskParam.put("title", dto.getTitle());
        taskParam.put("summary", article.getSummary());
        taskParam.put("content", contentText);
        taskParam.put("contentHtml", contentHtml);
        taskParam.put("contentParagraphs", contentParagraphs);
        taskParam.put("coverUrl", coverUrl);
        taskParam.put("videoUrl", videoUrl);
        taskParam.put("postType", postType);
        taskParam.put("refArticleId", dto.getRefArticleId());
        taskParam.put("categoryId", article.getCategoryId());
        taskParam.put("categoryIds", categoryIds);
        taskParam.put("imageUrls", articleImages);

        if (dto.getScheduledPublishTime() != null && dto.getScheduledPublishTime().isAfter(LocalDateTime.now())) {
            taskService.addDelayTask(ContentConstants.TaskType.ARTICLE_PUBLISH, taskParam, articleId, dto.getScheduledPublishTime());
            log.info("文章进入定时审核发布队列: articleId={}, executeTime={}", articleId, dto.getScheduledPublishTime());
        } else {
            taskService.addImmediateTask(ContentConstants.TaskType.ARTICLE_PUBLISH, taskParam, articleId);
            log.info("文章进入即时审核发布队列: articleId={}", articleId);
        }
        return articleId;
    }

    private int normalizePostType(Integer postType) {
        if (postType == null) {
            return ContentConstants.PostType.IMAGE_TEXT;
        }
        if (postType == ContentConstants.PostType.ARTICLE) {
            return ContentConstants.PostType.IMAGE_TEXT;
        }
        if (postType != ContentConstants.PostType.IMAGE_TEXT
                && postType != ContentConstants.PostType.VIDEO
                && postType != ContentConstants.PostType.REPOST) {
            throw new BusinessException("不支持的发帖模式");
        }
        return postType;
    }

    private Article resolveRefArticleIfRepost(int postType, String refArticleId) {
        if (postType != ContentConstants.PostType.REPOST) {
            return null;
        }
        return requirePublishedRefArticle(refArticleId);
    }

    private Article requirePublishedRefArticle(String refArticleId) {
        if (!StringUtils.hasText(refArticleId)) {
            throw new BusinessException("转发帖必须指定原帖");
        }
        Article refArticle = getByPublicId(refArticleId);
        if (refArticle == null || (refArticle.getDeleted() != null && refArticle.getDeleted() == 1)) {
            throw new BusinessException("原帖不存在");
        }
        if (!Objects.equals(refArticle.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            throw new BusinessException("仅可转发已发布的帖子");
        }
        return refArticle;
    }

    private List<Long> resolveCategoryIds(ArticleDTO dto, int postType, Article refArticle) {
        List<Long> ids = new ArrayList<>();
        if (dto.getCategoryIds() != null) {
            for (Long categoryId : dto.getCategoryIds()) {
                if (categoryId != null && !ids.contains(categoryId)) {
                    ids.add(categoryId);
                }
            }
        }
        if (ids.isEmpty() && dto.getCategoryId() != null) {
            ids.add(dto.getCategoryId());
        }
        if (ids.isEmpty() && postType == ContentConstants.PostType.REPOST && refArticle != null) {
            ids.addAll(ArticleCategoryHelper.resolveIds(refArticle));
        }
        if (ids.isEmpty()) {
            throw new BusinessException("请选择分类");
        }
        if (ids.size() > 3) {
            throw new BusinessException("最多选择3个分类");
        }
        return ids;
    }

    private void applyCategoryFilter(LambdaQueryWrapper<Article> wrapper, Long categoryId) {
        if (categoryId == null) {
            return;
        }
        wrapper.apply("EXISTS (SELECT 1 FROM t_article_category ac "
                + "WHERE ac.article_id = t_article.id AND ac.category_id = {0})", categoryId);
    }

    private String resolveCoverUrl(String coverUrl, List<String> images, int postType, Article refArticle) {
        if (postType == ContentConstants.PostType.REPOST) {
            if (StringUtils.hasText(coverUrl)) {
                return coverUrl.trim();
            }
            return refArticle == null ? null : refArticle.getCoverUrl();
        }
        if (StringUtils.hasText(coverUrl)) {
            return coverUrl.trim();
        }
        return images.isEmpty() ? null : images.get(0);
    }

    private List<String> normalizeArticleImages(List<String> imageUrls, String coverUrl, int postType) {
        List<String> images = new ArrayList<>();
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (StringUtils.hasText(imageUrl) && !images.contains(imageUrl.trim())) {
                    images.add(imageUrl.trim());
                }
            }
        }
        if (postType != ContentConstants.PostType.REPOST
                && images.isEmpty()
                && StringUtils.hasText(coverUrl)) {
            images.add(coverUrl.trim());
        }
        if (images.size() > ContentConstants.MediaLimit.IMAGE_MAX_COUNT) {
            throw new BusinessException("文章图片最多支持"
                    + ContentConstants.MediaLimit.IMAGE_MAX_COUNT + "张");
        }
        return images;
    }

    @Override
    public void deleteArticle(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在");
        }
        var content = articleContentService.getByArticleId(id);
        boolean deleted = articleDeletionPersistenceService.markDeleted(article, content);
        if (deleted) {
            log.info("文章删除状态已提交，异步清理资源: articleId={}", id);
        }
    }

    @Override
    public ArticleDetailVO getArticleDetail(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        assertReadable(article);
        return buildDetailVo(article, false);
    }

    @Override
    public ArticleDetailVO getArticleDetailForAdminPreview(String publicId) {
        Long id = resolvePublicId(publicId);
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        return buildDetailVo(article, true);
    }

    @Override
    public ArticleDetailVO getArticleDetailForOwner(Long id, Long userId) {
        Article article = requireOwnedArticle(id, userId);
        return buildDetailVo(article, true);
    }

    @Override
    public ArticleDetailVO getArticleDetailInternal(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        return buildDetailVo(article, false);
    }

    @Override
    public void assertContentReadable(Long articleId) {
        Article article = getById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        assertReadable(article);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitForAudit(Long id, Long userId) {
        Article article = requireOwnedArticle(id, userId);
        if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING)) {
            throw new BusinessException("文章已在审核中");
        }
        if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            throw new BusinessException("文章已发布");
        }
        var content = articleContentService.getByArticleId(id);
        if (content == null || !StringUtils.hasText(content.getContent())) {
            throw new BusinessException("请先完善正文再提交审核");
        }
        validateContent(content.getContent(), article.getPostType());
        if (Objects.equals(article.getPostType(), ContentConstants.PostType.VIDEO)
                && !StringUtils.hasText(article.getVideoUrl())) {
            throw new BusinessException("视频模式下请先完成视频上传");
        }

        article.setStatus(ContentConstants.ArticleStatus.PENDING);
        article.setAuditMessage("审核中");
        article.setPublishedTime(null);
        article.setUpdateTime(LocalDateTime.now());
        updateById(article);

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("articleId", id);
        taskParam.put("userId", userId);
        taskParam.put("title", article.getTitle());
        taskParam.put("summary", article.getSummary());
        taskParam.put("content", content.getContent());
        taskParam.put("contentHtml", content.getContentHtml());
        taskParam.put("contentParagraphs", content.getContentParagraphs());
        taskParam.put("coverUrl", article.getCoverUrl());
        taskParam.put("videoUrl", article.getVideoUrl());
        taskParam.put("postType", article.getPostType());
        taskParam.put("categoryId", article.getCategoryId());
        taskParam.put("imageUrls", content.getImageUrls() == null ? List.of() : content.getImageUrls());
        taskService.addImmediateTask(ContentConstants.TaskType.ARTICLE_PUBLISH, taskParam, id);
        articleSearchSyncProducer.delete(id);
        articleNotificationEventProducer.publishProfileInvalidation(userId,
                NotificationConstants.ProfileDataDomain.POSTS);
        log.info("文章提交审核: articleId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublishByAuthor(Long id, Long userId) {
        Article article = requireOwnedArticle(id, userId);
        taskService.cancelTasksByBusinessId(id, ContentConstants.TaskType.ARTICLE_PUBLISH);
        ArticleContent content = articleContentService.getByArticleId(id);
        // 取消上架：只改状态和黑名单，不搬运公有桶中的图片/视频。
        chunkUploadService.abortByArticleId(id, userId, false);
        article.setStatus(ContentConstants.ArticleStatus.DRAFT);
        article.setAuditMessage("作者下架，已移入草稿");
        article.setPublishedTime(null);
        article.setUpdateTime(LocalDateTime.now());
        updateById(article);
        articleMediaHelper.blacklistRefs(collectMediaRefs(article, content));
        articleSearchSyncProducer.delete(id);
        articleSocialFeedProducer.remove(id);
        articleNotificationEventProducer.publishProfileInvalidation(userId,
                NotificationConstants.ProfileDataDomain.POSTS);
        log.info("文章取消上架: articleId={}", id);
    }

    @Override
    public ArticleProgressVO getArticleProgress(Long id, Long userId) {
        Article article = requireOwnedArticle(id, userId);
        ArticleProgressVO vo = new ArticleProgressVO();
        // 进度 VO 面向作者侧公开接口，返回 publicId，不能泄露内部数据库主键。
        vo.setArticleId(article.getPublicId());
        vo.setStatus(article.getStatus());
        vo.setAuditMessage(article.getAuditMessage());
        vo.setPostType(article.getPostType());
        Task latestTask = taskService.getLatestTask(id, ContentConstants.TaskType.ARTICLE_PUBLISH);
        vo.setTaskStatus(latestTask == null ? null : latestTask.getStatus());
        if (latestTask != null && StringUtils.hasText(latestTask.getErrorMsg())) {
            vo.setTaskErrorMessage(latestTask.getErrorMsg());
        }

        ArticleAudit latestAudit = articleAuditMapper.selectOne(new LambdaQueryWrapper<ArticleAudit>()
                .eq(ArticleAudit::getArticleId, id)
                .orderByDesc(ArticleAudit::getId)
                .last("LIMIT 1"));
        if (latestAudit != null) {
            vo.setAuditStage(latestAudit.getAuditStage());
            vo.setAuditStageText(auditStageText(latestAudit.getAuditStage()));
        } else if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PENDING)) {
            vo.setAuditStageText("排队审核中");
        } else if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            vo.setAuditStageText("已发布");
        } else if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.REJECTED)) {
            vo.setAuditStageText("已驳回");
        } else if (Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.OFFLINE)) {
            vo.setAuditStageText("已取消上架");
        } else {
            vo.setAuditStageText("草稿");
        }

        List<ChunkUploadStatusVO> uploads = chunkUploadService.listByArticleId(id, userId);
        List<String> activeIds = new ArrayList<>();
        Integer uploadPercent = null;
        String uploadStatus = null;
        for (ChunkUploadStatusVO upload : uploads) {
            activeIds.add(upload.getUploadId());
            if ("UPLOADING".equals(upload.getStatus())) {
                uploadPercent = upload.getPercent();
                uploadStatus = upload.getStatus();
            } else if (uploadPercent == null) {
                uploadPercent = upload.getPercent();
                uploadStatus = upload.getStatus();
            }
        }
        if (uploadPercent == null
                && (StringUtils.hasText(article.getVideoUrl()) || StringUtils.hasText(article.getCoverUrl()))) {
            uploadPercent = 100;
            uploadStatus = "MERGED";
        }
        vo.setUploadPercent(uploadPercent);
        vo.setUploadStatus(uploadStatus);
        vo.setActiveUploadIds(activeIds);
        return vo;
    }

    private String auditStageText(Integer stage) {
        if (stage == null) {
            return "审核中";
        }
        return switch (stage) {
            case ContentConstants.AuditStage.LOCAL_TEXT -> "本地文本审核";
            case ContentConstants.AuditStage.AI_TEXT -> "AI 文本审核";
            case ContentConstants.AuditStage.AI_IMAGE -> "AI 图片审核";
            default -> "审核中";
        };
    }

    private void assertReadable(Article article) {
        if (!Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            throw new BusinessException("文章不存在或无权查看");
        }
    }

    private ArticleDetailVO buildDetailVo(Article article, boolean resolvePreview) {
        Long viewerId = UserThreadLocal.getUserId();
        boolean ownerOrAdmin = (viewerId != null && Objects.equals(viewerId, article.getUserId()))
                || (UserThreadLocal.getType() != null
                && UserThreadLocal.getType() == AccountType.ADMIN.getCode());
        boolean needPreview = resolvePreview && ownerOrAdmin
                && !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED);

        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setId(article.getId());
        vo.setPublicId(article.getPublicId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        String coverRef = needPreview ? article.getCoverUrl() : null;
        vo.setCoverUrl(needPreview ? articleMediaHelper.resolvePreview(coverRef)
                : articleMediaHelper.resolvePublic(article.getCoverUrl(), ownerOrAdmin));
        vo.setCoverRef(coverRef);
        vo.setPostType(article.getPostType());
        vo.setRefArticleId(article.getRefArticleId());
        Article refArticle = article.getRefArticleId() == null
                ? null
                : getByPublicId(article.getRefArticleId());
        List<Long> authorIds = new ArrayList<>();
        if (article.getUserId() != null) {
            authorIds.add(article.getUserId());
        }
        if (refArticle != null && refArticle.getUserId() != null) {
            authorIds.add(refArticle.getUserId());
        }
        Map<Long, UserCardInternalVO> authorMap = fetchUsers(authorIds);
        UserCardInternalVO author = authorMap.get(article.getUserId());
        if (author != null) {
            vo.setAuthorAccountId(author.getAccountId());
            vo.setUsername(author.getUsername());
            vo.setAvatar(articleMediaHelper.resolvePublic(author.getAvatar()));
        }
        if (isPublishedRefArticle(refArticle)) {
            vo.setRefArticle(buildRefVo(refArticle, authorMap));
        }
        String videoRef = needPreview ? article.getVideoUrl() : null;
        vo.setVideoUrl(needPreview ? articleMediaHelper.resolvePreview(videoRef)
                : articleMediaHelper.resolvePublic(article.getVideoUrl(), ownerOrAdmin));
        vo.setVideoRef(videoRef);
        vo.setCategoryId(article.getCategoryId());
        vo.setCategoryIds(ArticleCategoryHelper.resolveIds(article));
        vo.setStatus(article.getStatus());
        vo.setAuditMessage(article.getAuditMessage());
        vo.setScheduledPublishTime(article.getScheduledPublishTime());
        vo.setPublishedTime(article.getPublishedTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());

        var content = articleContentService.getByArticleId(article.getId());
        if (content != null) {
            vo.setContent(content.getContent());
            vo.setContentHtml(content.getContentHtml());
            vo.setContentParagraphs(content.getContentParagraphs());
            if (needPreview && content.getImageUrls() != null) {
                List<String> refs = new ArrayList<>();
                List<String> previews = new ArrayList<>();
                for (String image : content.getImageUrls()) {
                    String ref = image;
                    refs.add(ref);
                    previews.add(articleMediaHelper.resolvePreview(ref));
                }
                vo.setImageRefs(refs);
                vo.setImageUrls(previews);
            } else {
                vo.setImageUrls(content.getImageUrls().stream()
                        .map(image -> articleMediaHelper.resolvePublic(image, ownerOrAdmin))
                        .toList());
            }
        }
        articleCategoryService.enrichDetailVO(vo);
        vo.setGameTags(articleGameService.listByArticle(article.getId()));
        return vo;
    }

    private boolean isPublishedRefArticle(Article refArticle) {
        return refArticle != null
                && Objects.equals(refArticle.getStatus(), ContentConstants.ArticleStatus.PUBLISHED);
    }

    private ArticleRefVO buildRefVo(Article refArticle, Map<Long, UserCardInternalVO> authorMap) {
        ArticleRefVO refVo = new ArticleRefVO();
        refVo.setId(refArticle.getPublicId());
        refVo.setTitle(refArticle.getTitle());
        refVo.setSummary(refArticle.getSummary());
        refVo.setCoverUrl(articleMediaHelper.resolvePublic(refArticle.getCoverUrl()));
        refVo.setVideoUrl(articleMediaHelper.resolvePublic(refArticle.getVideoUrl()));
        refVo.setPostType(refArticle.getPostType());
        UserCardInternalVO author = authorMap.get(refArticle.getUserId());
        if (author != null) {
            refVo.setAuthorAccountId(author.getAccountId());
            refVo.setUsername(author.getUsername());
            refVo.setAvatar(articleMediaHelper.resolvePublic(author.getAvatar()));
        }
        return refVo;
    }

    private Map<Long, UserCardInternalVO> fetchUsers(List<Long> userIds) {
        List<Long> distinctIds = userIds == null ? List.of() : userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }
        try {
            var result = userFeignClient.getUsersByUserIds(distinctIds);
            if (result == null || result.getCode() == null || result.getCode() != 200
                    || result.getData() == null) {
                return Map.of();
            }
            return result.getData().stream()
                    .filter(item -> item.getUserId() != null)
                    .collect(Collectors.toMap(UserCardInternalVO::getUserId, item -> item, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: userIds={}", distinctIds, e);
            return Map.of();
        }
    }

    private UserCardInternalVO fetchUserByAccountId(Long accountId) {
        if (accountId == null) {
            return null;
        }
        try {
            var result = userFeignClient.getUserByAccountId(accountId);
            if (result == null || result.getCode() == null || result.getCode() != 200) {
                return null;
            }
            return result.getData();
        } catch (Exception e) {
            log.warn("获取用户信息失败: accountId={}", accountId, e);
            return null;
        }
    }

    @Override
    public Page<Article> getArticlePage(Integer page, Integer size, Long categoryId, Integer status) {
        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (categoryId != null) {
            applyCategoryFilter(wrapper, categoryId);
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.PUBLISHED)) {
            wrapper.eq(Article::getStatus, status);
        }
        wrapper.orderByDesc(Article::getPublishedTime).orderByDesc(Article::getId);
        return page(pageParam, wrapper);
    }

    @Override
    public List<Article> getUserArticles(Long userId) {
        return list(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId)
                .orderByDesc(Article::getUpdateTime)
                .orderByDesc(Article::getId));
    }

    @Override
    public Page<Article> getUserArticlesPage(Long userId, Integer page, Integer size, String tab) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, userId);
        if ("published".equals(tab)) {
            wrapper.eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        } else if ("draft".equals(tab)) {
            wrapper.in(Article::getStatus,
                    ContentConstants.ArticleStatus.DRAFT,
                    ContentConstants.ArticleStatus.OFFLINE);
        } else if ("unpublished".equals(tab)) {
            wrapper.in(Article::getStatus,
                    ContentConstants.ArticleStatus.PENDING,
                    ContentConstants.ArticleStatus.REJECTED);
        }
        wrapper.orderByDesc(Article::getUpdateTime).orderByDesc(Article::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Article> getLatestArticles(Long categoryId, Integer size) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (categoryId != null) {
            applyCategoryFilter(wrapper, categoryId);
        }
        wrapper.orderByDesc(Article::getPublishedTime)
                .orderByDesc(Article::getId)
                .last("LIMIT " + Math.max(size, 1));
        return list(wrapper);
    }

    @Override
    public List<Article> getMoreArticles(Long categoryId, Long lastId, Integer size) {
        if (lastId == null) {
            return List.of();
        }
        Article lastArticle = getById(lastId);
        if (lastArticle == null) {
            return List.of();
        }
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (categoryId != null) {
            applyCategoryFilter(wrapper, categoryId);
        }
        wrapper.and(q -> q.lt(Article::getPublishedTime, lastArticle.getPublishedTime())
                .or()
                .eq(Article::getPublishedTime, lastArticle.getPublishedTime()).lt(Article::getId, lastId));
        wrapper.orderByDesc(Article::getPublishedTime)
                .orderByDesc(Article::getId)
                .last("LIMIT " + Math.max(size, 1));
        return list(wrapper);
    }

    @Override
    public Page<Article> getFollowArticles(Long userId, Integer page, Integer size) {
        Set<String> followUserIdSet = redisUtils.setMembers(ContentConstants.FOLLOW_KEY_PREFIX + userId);
        if (followUserIdSet.isEmpty()) {
            return new Page<>(page, size);
        }
        List<Long> followUserIds = followUserIdSet.stream()
                .map(Long::parseLong)
                .collect(Collectors.toList());
        return page(new Page<>(page, size), new LambdaQueryWrapper<Article>()
                .in(Article::getUserId, followUserIds)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                .orderByDesc(Article::getPublishedTime)
                .orderByDesc(Article::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticleStatus(Long id, Integer status) {
        Article article = getById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在");
        }
        boolean wasPublished = Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED);
        boolean willBePublished = Objects.equals(status, ContentConstants.ArticleStatus.PUBLISHED);
        boolean newlyPublished = !wasPublished && willBePublished;
        ArticleContent content = articleContentService.getByArticleId(id);
        List<String> mediaRefs = collectMediaRefs(article, content);
        if (newlyPublished) {
            ArticleMediaHelper.PromotedGallery gallery = articleMediaHelper.promoteGallery(
                    article.getCoverUrl(), content == null ? List.of() : content.getImageUrls(), "content");
            article.setCoverUrl(gallery.coverUrl());
            article.setVideoUrl(articleMediaHelper.promoteToPublic(article.getVideoUrl(), "video"));
            if (content != null) {
                articleContentService.saveContent(id, content.getContent(), content.getContentHtml(),
                        content.getContentParagraphs(), gallery.imageUrls(), article.getUserId());
            }
        }
        LocalDateTime publishedTime = newlyPublished ? LocalDateTime.now() : article.getPublishedTime();
        article.setStatus(status);
        article.setUpdateTime(LocalDateTime.now());
        if (newlyPublished) {
            article.setPublishedTime(publishedTime);
            article.setAuditMessage("重新上架");
        }
        if (Objects.equals(status, ContentConstants.ArticleStatus.OFFLINE)) {
            article.setAuditMessage("管理员下架");
        }
        updateById(article);
        if (wasPublished && !willBePublished) {
            articleMediaHelper.blacklistRefs(mediaRefs);
        }
        if (newlyPublished) {
            articleMediaHelper.removeFromBlacklist(mediaRefs);
        }
        if (wasPublished && !Objects.equals(status, ContentConstants.ArticleStatus.PUBLISHED)) {
            articleSocialFeedProducer.remove(id);
        }
        if (newlyPublished) {
            articleSocialFeedProducer.publish(article.getUserId(), id, publishedTime);
            articleNotificationEventProducer.publishProfileInvalidation(article.getUserId(),
                    NotificationConstants.ProfileDataDomain.POSTS);
        }
        if (willBePublished) {
            articleSearchSyncProducer.upsert(id);
        } else {
            articleSearchSyncProducer.delete(id);
        }
    }

    @Override
    public List<Article> listPublishedArticles() {
        return list(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                .orderByDesc(Article::getPublishedTime)
                .orderByDesc(Article::getId));
    }

    @Override
    public PageResult<Article> listPublishedArticlesPage(Integer page, Integer size) {
        Page<Article> result = getArticlePage(page, size, null, ContentConstants.ArticleStatus.PUBLISHED);
        return PageResult.of(result.getRecords(), (long) result.getCurrent(), (long) result.getSize(), result.getTotal());
    }

    @Override
    public Page<Article> getArticlePageAdmin(Integer page, Integer size, String keyword, Long categoryId, Integer status, Long authorId) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Article::getTitle, keyword).or().like(Article::getSummary, keyword));
        }
        if (categoryId != null) {
            applyCategoryFilter(wrapper, categoryId);
        }
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        if (authorId != null) {
            wrapper.eq(Article::getUserId, authorId);
        }
        wrapper.orderByDesc(Article::getUpdateTime).orderByDesc(Article::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticleAdmin(Long articleId) {
        deleteArticle(articleId);
    }

    @Override
    public List<Article> listByUserIdsAndStatus(List<Long> userIds, Integer status, int limit) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Article::getUserId, userIds);
        if (status != null) {
            wrapper.eq(Article::getStatus, status);
        }
        wrapper.orderByDesc(Article::getPublishedTime).orderByDesc(Article::getId).last("LIMIT " + Math.max(limit, 1));
        return list(wrapper);
    }

    @Override
    public List<Article> listPublishedByAuthor(Long authorId, int limit) {
        if (authorId == null) {
            return List.of();
        }
        return list(new LambdaQueryWrapper<Article>()
                .eq(Article::getUserId, authorId)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)
                .orderByDesc(Article::getPublishedTime)
                .orderByDesc(Article::getId)
                .last("LIMIT " + Math.max(limit, 1)));
    }

    @Override
    public List<Article> listPublishedByAuthorsBefore(List<Long> authorIds, LocalDateTime before, Long beforeArticleId, int limit) {
        if (authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Article::getUserId, authorIds)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (before != null) {
            wrapper.and(nested -> nested.lt(Article::getPublishedTime, before)
                    .or(equalTime -> equalTime.eq(Article::getPublishedTime, before)
                            .lt(beforeArticleId != null, Article::getId, beforeArticleId)));
        }
        wrapper.orderByDesc(Article::getPublishedTime)
                .orderByDesc(Article::getId)
                .last("LIMIT " + Math.max(limit, 1));
        return list(wrapper);
    }

    @Override
    public Long countArticle() {
        return count();
    }

    private Article requireOwnedArticle(Long articleId, Long userId) {
        Article article = getById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (!Objects.equals(article.getUserId(), userId)) {
            throw new BusinessException("无权操作他人文章");
        }
        return article;
    }

    public Article getByPublicId(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            return null;
        }
        return getOne(new LambdaQueryWrapper<Article>()
                .eq(Article::getPublicId, publicId.trim())
                .last("LIMIT 1"));
    }

    private String newPublicId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private List<String> collectMediaRefs(Article article, ArticleContent content) {
        Set<String> refs = new HashSet<>();
        if (article != null) {
            if (StringUtils.hasText(article.getCoverUrl())) {
                refs.add(article.getCoverUrl());
            }
            if (StringUtils.hasText(article.getVideoUrl())) {
                refs.add(article.getVideoUrl());
            }
        }
        if (content != null && content.getImageUrls() != null) {
            content.getImageUrls().stream()
                    .filter(StringUtils::hasText)
                    .forEach(refs::add);
        }
        return new ArrayList<>(refs);
    }

    private void cleanupReplacedMedia(List<String> oldRefs, List<String> currentRefs) {
        if (oldRefs == null || oldRefs.isEmpty()) {
            return;
        }
        Set<String> current = (currentRefs == null ? List.<String>of() : currentRefs).stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet());
        oldRefs.stream()
                .filter(StringUtils::hasText)
                .filter(ref -> !current.contains(ref.trim()))
                .forEach(articleMediaHelper::deleteRef);
    }

    private void validatePublishTime(LocalDateTime publishTime) {
        if (publishTime != null && publishTime.isBefore(LocalDateTime.now().minusMinutes(1))) {
            throw new BusinessException("定时发布时间不能早于当前时间");
        }
    }

    private String buildSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }

    private Map<String, String> normalizeParagraphs(ArticleDTO dto) {
        Map<String, String> result = new LinkedHashMap<>();
        if (dto.getContentParagraphs() != null && !dto.getContentParagraphs().isEmpty()) {
            dto.getContentParagraphs().forEach((key, value) -> {
                if (StringUtils.hasText(value)) {
                    String normalizedKey = StringUtils.hasText(key) ? key.trim() : "p" + (result.size() + 1);
                    result.put(normalizedKey, value.trim());
                }
            });
        }
        if (result.isEmpty() && StringUtils.hasText(dto.getContent())) {
            String normalized = dto.getContent().trim();
            if (normalized.startsWith("{{") && normalized.endsWith("}}")) {
                normalized = normalized.substring(2, normalized.length() - 2);
            }
            String[] paragraphs = normalized.split("\\}\\s*,\\s*\\{|\n{2,}");
            for (String paragraph : paragraphs) {
                String clean = paragraph.replace("{", "").replace("}", "").trim();
                if (StringUtils.hasText(clean)) {
                    result.put("p" + (result.size() + 1), clean);
                }
            }
        }
        return result;
    }

    private String joinParagraphs(Map<String, String> paragraphs) {
        return String.join("\n\n", paragraphs.values());
    }

    private void validateContent(String contentText, int postType) {
        if (postType == ContentConstants.PostType.REPOST) {
            if (contentText != null && contentText.length() > MAX_CONTENT_LENGTH) {
                throw new BusinessException("转发附言不能超过" + MAX_CONTENT_LENGTH + "字");
            }
            return;
        }
        if (postType == ContentConstants.PostType.VIDEO) {
            // 视频介绍允许为空，但提交审核时仍建议有简介
            if (contentText != null && contentText.length() > MAX_CONTENT_LENGTH) {
                throw new BusinessException("视频介绍不能超过" + MAX_CONTENT_LENGTH + "字");
            }
            return;
        }
        if (!StringUtils.hasText(contentText)) {
            throw new BusinessException("文章内容不能为空");
        }
        if (contentText.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("文章正文不能超过" + MAX_CONTENT_LENGTH + "字");
        }
    }

    private Long requireCurrentUserId() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }

    private PageResult<ArticleListVO> toListPageResult(Page<Article> page) {
        return PageResult.of(toEnrichedListVOs(page.getRecords()),
                page.getCurrent(), page.getSize(), page.getTotal());
    }

    private List<ArticleListVO> toEnrichedListVOs(List<Article> articles) {
        return toEnrichedListVOs(articles, false);
    }

    private List<ArticleListVO> toEnrichedListVOs(List<Article> articles, boolean resolvePrivatePreview) {
        List<ArticleListVO> records = ArticleConverter.toListVOs(articles);
        for (int i = 0; i < records.size(); i++) {
            Article article = articles.get(i);
            ArticleListVO record = records.get(i);
            if (resolvePrivatePreview && !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
                    record.setCoverUrl(articleMediaHelper.resolvePreview(article.getCoverUrl()));
                    record.setVideoUrl(articleMediaHelper.resolvePreview(article.getVideoUrl()));
            } else {
                record.setCoverUrl(articleMediaHelper.resolvePublic(article.getCoverUrl()));
                record.setVideoUrl(articleMediaHelper.resolvePublic(article.getVideoUrl()));
            }
        }
        articleAuthorEnricher.enrich(articles, records);
        articleCategoryService.enrichListVOs(records);
        articleGameService.enrichListVOs(records);
        return records;
    }

    @Override
    public Result<String> saveArticleForCurrentUser(ArticleDTO dto) {
        // 明确指定 data 类型，避免 String 重载把公开 ID 写进 message 字段。
        return Result.success("操作成功", getPublicId(saveArticle(dto, requireCurrentUserId())));
    }

    @Override
    public Result<String> updateArticleForCurrentUser(String publicId, ArticleDTO dto) {
        Long userId = requireCurrentUserId();
        dto.setId(resolvePublicId(publicId));
        // 明确指定 data 类型，避免 String 重载把公开 ID 写进 message 字段。
        return Result.success("操作成功", getPublicId(saveArticle(dto, userId)));
    }

    @Override
    public Result<Void> deleteArticleForCurrentUser(String publicId) {
        Long userId = requireCurrentUserId();
        Long id = resolvePublicId(publicId);
        Article article = getById(id);
        if (article == null || !Objects.equals(article.getUserId(), userId)) {
            return Result.error("文章不存在或无权删除");
        }
        deleteArticle(id);
        return Result.success(null);
    }

    @Override
    public Result<ArticleDetailVO> queryArticleDetail(String publicId) {
        return Result.success(getArticleDetail(resolvePublicId(publicId)));
    }

    @Override
    public Result<ArticleDetailVO> queryArticleDetailForOwner(String publicId) {
        Long userId = requireCurrentUserId();
        return Result.success(getArticleDetailForOwner(resolvePublicId(publicId), userId));
    }

    @Override
    public Result<ArticleContentVO> queryArticleContent(String publicId) {
        Long id = resolvePublicId(publicId);
        assertContentReadable(id);
        ArticleContentVO contentVO = ArticleConverter.toContentVO(articleContentService.getByArticleId(id));
        if (contentVO != null && contentVO.getImageUrls() != null) {
            contentVO.setImageUrls(contentVO.getImageUrls().stream()
                    .map(articleMediaHelper::resolvePublic)
                    .toList());
        }
        return Result.success(contentVO);
    }

    @Override
    public PageResult<ArticleListVO> queryArticlePage(Integer page, Integer size, Long categoryId, Integer status) {
        return toListPageResult(getArticlePage(page, size, categoryId, status));
    }

    @Override
    public PageResult<ArticleListVO> queryMyArticlesPage(Integer page, Integer size, String tab) {
        Page<Article> pageResult = getUserArticlesPage(requireCurrentUserId(), page, size, tab);
        return PageResult.of(toEnrichedListVOs(pageResult.getRecords(), true),
                pageResult.getCurrent(), pageResult.getSize(), pageResult.getTotal());
    }

    @Override
    public PageResult<ArticleListVO> queryFollowArticles(Integer page, Integer size) {
        return toListPageResult(getFollowArticles(requireCurrentUserId(), page, size));
    }

    @Override
    public Result<List<ArticleListVO>> queryLatestArticles(Long categoryId, Integer size) {
        return Result.success(toEnrichedListVOs(getLatestArticles(categoryId, size)));
    }

    @Override
    public Result<List<ArticleListVO>> queryMoreArticles(Long categoryId, String lastPublicId, Integer size) {
        return Result.success(toEnrichedListVOs(
                getMoreArticles(categoryId, resolvePublicId(lastPublicId), size)));
    }

    @Override
    public Result<ArticleProgressVO> queryArticleProgress(String publicId) {
        Long userId = requireCurrentUserId();
        return Result.success(getArticleProgress(resolvePublicId(publicId), userId));
    }

    @Override
    public Result<Void> submitPublish(String publicId) {
        Long userId = requireCurrentUserId();
        submitForAudit(resolvePublicId(publicId), userId);
        return Result.success(null);
    }

    @Override
    public Result<Void> submitUnpublish(String publicId) {
        Long userId = requireCurrentUserId();
        unpublishByAuthor(resolvePublicId(publicId), userId);
        return Result.success(null);
    }

    @Override
    public Result<List<ArticleListVO>> queryPublishedList() {
        return Result.success(toEnrichedListVOs(listPublishedArticles()));
    }

    @Override
    public Result<PageResult<ArticleListVO>> queryPublishedPage(Integer page, Integer size) {
        PageResult<Article> pageResult = listPublishedArticlesPage(page, size);
        return Result.success(PageResult.of(toEnrichedListVOs(pageResult.getData()),
                pageResult.getPage(), pageResult.getSize(), pageResult.getTotal()));
    }

    @Override
    public Result<List<ArticleListVO>> queryByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success(List.of());
        }
        List<ArticleListVO> records = toEnrichedListVOs(list(new LambdaQueryWrapper<Article>()
                .in(Article::getId, ids)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED)));
        return Result.success(records);
    }

    @Override
    public Result<List<ArticleListVO>> queryByPublicIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<Article> articles = list(new LambdaQueryWrapper<Article>()
                .in(Article::getPublicId, publicIds)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED));
        Map<String, Article> articleMap = articles.stream()
                .collect(Collectors.toMap(Article::getPublicId, article -> article, (left, right) -> left));
        List<Article> ordered = publicIds.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .toList();
        return Result.success(toEnrichedListVOs(ordered));
    }

    @Override
    public Long resolvePublicId(String publicId) {
        Article article = getByPublicId(publicId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        return article.getId();
    }

    @Override
    public String getPublicId(Long articleId) {
        Article article = articleId == null ? null : getById(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        return article.getPublicId();
    }

    @Override
    public Result<Map<Long, List<Long>>> queryCategoryIdsByArticleIds(List<Long> articleIds) {
        return Result.success(articleCategoryService.mapCategoryIdsByArticleIds(articleIds));
    }

    @Override
    public Result<List<ArticleListVO>> queryPublishedByAuthor(Long authorId, Integer size) {
        return Result.success(toEnrichedListVOs(listPublishedByAuthor(authorId, size)));
    }

    @Override
    public Result<List<ArticleListVO>> queryPublishedByAccountId(Long accountId, Integer size) {
        UserCardInternalVO author = fetchUserByAccountId(accountId);
        if (author == null || author.getUserId() == null) {
            return Result.success(List.of());
        }
        return queryPublishedByAuthor(author.getUserId(), size);
    }

    @Override
    public Result<List<ArticleListVO>> queryPublishedByAuthors(List<Long> authorIds, String before, Long beforeArticleId, Integer size) {
        LocalDateTime beforeTime = before == null || before.isBlank() ? null : LocalDateTime.parse(before);
        return Result.success(toEnrichedListVOs(
                listPublishedByAuthorsBefore(authorIds, beforeTime, beforeArticleId, size)));
    }

    @Override
    public PageResult<ArticleListVO> queryArticlePageAdmin(Integer page, Integer size, String keyword,
                                                           Long categoryId, Integer status, Long authorId) {
        return toListPageResult(getArticlePageAdmin(page, size, keyword, categoryId, status, authorId));
    }

    @Override
    public Result<Void> deleteArticleAdminOp(Long articleId) {
        deleteArticleAdmin(articleId);
        return Result.success(null);
    }

    @Override
    public Result<Void> updateArticleStatusAdmin(Long articleId, Integer status) {
        updateArticleStatus(articleId, status);
        return Result.success(null);
    }

    @Override
    public Result<Long> countArticles() {
        return Result.success(countArticle());
    }

    @Override
    public Result<ArticleDetailVO> queryArticleDetailInternal(Long id) {
        return Result.success(getArticleDetailInternal(id));
    }

    @Override
    public Result<Void> updateArticleStatusForFeign(Long articleId, Integer status) {
        updateArticleStatus(articleId, status);
        return Result.success(null);
    }

    @Override
    public Result<PageResult<ArticleListVO>> queryPublishedPageForFeign(Integer page, Integer size) {
        return queryPublishedPage(page, size);
    }
}
