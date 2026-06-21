package com.game.community.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.content.event.ArticleSearchSyncProducer;
import com.game.community.content.mapper.ArticleMapper;
import com.game.community.content.service.ArticleContentService;
import com.game.community.content.service.ArticleService;
import com.game.community.content.service.TaskService;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    private static final int MAX_ARTICLE_IMAGE_COUNT = 10;

    private static final int MAX_CONTENT_LENGTH = 800;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveArticle(ArticleDTO dto, Long userId) {
        validatePublishTime(dto.getScheduledPublishTime());
        List<String> articleImages = normalizeArticleImages(dto.getImageUrls(), dto.getCoverUrl());
        String coverUrl = articleImages.isEmpty() ? null : articleImages.get(0);
        Map<String, String> contentParagraphs = normalizeParagraphs(dto);
        String contentText = joinParagraphs(contentParagraphs);
        validateContent(contentText);

        boolean isUpdate = dto.getId() != null;
        Article article = isUpdate ? requireOwnedArticle(dto.getId(), userId) : new Article();
        if (!isUpdate) {
            article.setUserId(userId);
            article.setCreateTime(LocalDateTime.now());
            article.setDeleted(0);
        }

        article.setTitle(dto.getTitle().trim());
        article.setSummary(StringUtils.hasText(dto.getSummary()) ? dto.getSummary().trim() : buildSummary(contentText));
        article.setCoverUrl(coverUrl);
        article.setCategoryId(dto.getCategoryId());
        article.setScheduledPublishTime(dto.getScheduledPublishTime());
        article.setUpdateTime(LocalDateTime.now());

        boolean draft = Objects.equals(dto.getStatus(), ContentConstants.ArticleStatus.DRAFT);
        if (draft) {
            article.setStatus(ContentConstants.ArticleStatus.DRAFT);
            article.setAuditMessage(null);
            article.setPublishedTime(null);
            saveOrUpdate(article);
            articleContentService.saveContent(article.getId(), contentText, contentParagraphs, articleImages, userId);
            articleSearchSyncProducer.delete(article.getId());
            log.info("文章草稿保存成功: articleId={}, userId={}", article.getId(), userId);
            return article.getId();
        }

        article.setStatus(ContentConstants.ArticleStatus.PENDING);
        article.setAuditMessage("审核中");
        article.setPublishedTime(null);
        saveOrUpdate(article);
        Long articleId = article.getId();
        articleContentService.saveContent(articleId, contentText, contentParagraphs, articleImages, userId);

        Map<String, Object> taskParam = new HashMap<>();
        taskParam.put("articleId", articleId);
        taskParam.put("userId", userId);
        taskParam.put("title", dto.getTitle());
        taskParam.put("summary", article.getSummary());
        taskParam.put("content", contentText);
        taskParam.put("contentParagraphs", contentParagraphs);
        taskParam.put("coverUrl", coverUrl);
        taskParam.put("categoryId", dto.getCategoryId());
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

    private List<String> normalizeArticleImages(List<String> imageUrls, String coverUrl) {
        List<String> images = new ArrayList<>();
        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (StringUtils.hasText(imageUrl) && !images.contains(imageUrl.trim())) {
                    images.add(imageUrl.trim());
                }
            }
        }
        if (images.isEmpty() && StringUtils.hasText(coverUrl)) {
            images.add(coverUrl.trim());
        }
        if (images.size() > MAX_ARTICLE_IMAGE_COUNT) {
            throw new BusinessException("文章图片最多支持10张");
        }
        return images;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteArticle(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在");
        }
        articleContentService.deleteByArticleId(id);
        update(new LambdaUpdateWrapper<Article>()
                .eq(Article::getId, id)
                .set(Article::getDeleted, 1)
                .set(Article::getUpdateTime, LocalDateTime.now()));
        articleSearchSyncProducer.delete(id);
        log.info("文章删除成功: articleId={}", id);
    }

    @Override
    public ArticleDetailVO getArticleDetail(Long id) {
        Article article = getById(id);
        if (article == null) {
            throw new IllegalArgumentException("文章不存在");
        }
        ArticleDetailVO vo = new ArticleDetailVO();
        vo.setId(article.getId());
        vo.setUserId(article.getUserId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setCategoryId(article.getCategoryId());
        vo.setStatus(article.getStatus());
        vo.setAuditMessage(article.getAuditMessage());
        vo.setScheduledPublishTime(article.getScheduledPublishTime());
        vo.setPublishedTime(article.getPublishedTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());

        var content = articleContentService.getByArticleId(id);
        if (content != null) {
            vo.setContent(content.getContent());
            vo.setContentParagraphs(content.getContentParagraphs());
            vo.setImageUrls(content.getImageUrls());
        }
        return vo;
    }

    @Override
    public Page<Article> getArticlePage(Integer page, Integer size, Long categoryId, Integer status) {
        Page<Article> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
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
    public List<Article> getLatestArticles(Long categoryId, Integer size) {
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (categoryId != null) {
            wrapper.eq(Article::getCategoryId, categoryId);
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
            wrapper.eq(Article::getCategoryId, categoryId);
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
        article.setStatus(status);
        article.setUpdateTime(LocalDateTime.now());
        if (Objects.equals(status, ContentConstants.ArticleStatus.OFFLINE)) {
            article.setAuditMessage("管理员下架");
        }
        updateById(article);
        if (Objects.equals(status, ContentConstants.ArticleStatus.PUBLISHED)) {
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
            wrapper.eq(Article::getCategoryId, categoryId);
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
    public List<Article> listPublishedByAuthorsBefore(List<Long> authorIds, LocalDateTime before, int limit) {
        if (authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Article::getUserId, authorIds)
                .eq(Article::getStatus, ContentConstants.ArticleStatus.PUBLISHED);
        if (before != null) {
            wrapper.lt(Article::getPublishedTime, before);
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
            throw new IllegalArgumentException("文章不存在");
        }
        if (!Objects.equals(article.getUserId(), userId)) {
            throw new IllegalArgumentException("无权操作他人文章");
        }
        return article;
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

    private void validateContent(String contentText) {
        if (!StringUtils.hasText(contentText)) {
            throw new BusinessException("文章内容不能为空");
        }
        if (contentText.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("文章正文不能超过800字");
        }
    }
}
