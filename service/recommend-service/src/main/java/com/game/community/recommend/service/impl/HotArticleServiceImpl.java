package com.game.community.recommend.service.impl;

import com.game.community.common.constant.RecommendConstants;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.article.Category;
import com.game.community.model.vo.article.HotArticleVO;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.recommend.service.HotArticleService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotArticleServiceImpl implements HotArticleService {

    private static final int REBUILD_PAGE_SIZE = 100;

    private final RedisUtils redisUtils;
    private final ContentFeignClient contentFeignClient;
    private final SocialFeignClient socialFeignClient;
    private final UserFeignClient userFeignClient;

    @Override
    public void calculateHotArticles() {
        log.info("开始重建文章热榜");
        redisUtils.del(RecommendConstants.HOT_ARTICLE_RANK_KEY);
        for (Category category : enabledCategories()) {
            redisUtils.del(categoryKey(category.getId()));
        }

        long page = 1L;
        while (true) {
            PageResult<Article> result = unwrap(contentFeignClient.listPublishedArticlesPage((int) page, REBUILD_PAGE_SIZE));
            List<Article> articles = result == null ? List.of() : safeList(result.getData());
            if (articles.isEmpty()) {
                break;
            }
            writeScores(articles);
            if (articles.size() < REBUILD_PAGE_SIZE) {
                break;
            }
            page++;
        }
        log.info("文章热榜重建完成");
    }

    @Override
    public void updateHotScore(Long articleId) {
        if (articleId == null) {
            return;
        }
        List<Article> articles = safeList(unwrap(contentFeignClient.listArticlesByIds(List.of(articleId))));
        Article article = articles.stream().filter(item -> Objects.equals(item.getId(), articleId)).findFirst().orElse(null);
        if (article == null || !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            removeArticle(articleId);
            return;
        }
        writeScores(List.of(article));
    }

    @Override
    public PageResult<HotArticleVO> listHotArticles(Long userId, Long page, Long size) {
        ensureRankReady(RecommendConstants.HOT_ARTICLE_RANK_KEY);
        return listByRankKey(userId, RecommendConstants.HOT_ARTICLE_RANK_KEY, page, size);
    }

    @Override
    public PageResult<HotArticleVO> listCategoryHotArticles(Long userId, Long categoryId, Long page, Long size) {
        String key = categoryKey(categoryId);
        ensureRankReady(key);
        return listByRankKey(userId, key, page, size);
    }

    private void writeScores(List<Article> articles) {
        if (articles.isEmpty()) {
            return;
        }
        Map<Long, ArticleStatsVO> statsMap = statsMap(null, articles.stream().map(Article::getId).toList());
        for (Article article : articles) {
            ArticleStatsVO stats = statsMap.getOrDefault(article.getId(), emptyStats(article.getId()));
            double score = calculateScore(stats);
            String articleId = article.getId().toString();
            redisUtils.zAdd(RecommendConstants.HOT_ARTICLE_RANK_KEY, articleId, score);
            if (article.getCategoryId() != null) {
                redisUtils.zAdd(categoryKey(article.getCategoryId()), articleId, score);
                trimRank(categoryKey(article.getCategoryId()));
            }
        }
        trimRank(RecommendConstants.HOT_ARTICLE_RANK_KEY);
    }

    private PageResult<HotArticleVO> listByRankKey(Long userId, String key, Long pageValue, Long sizeValue) {
        long page = normalizePage(pageValue);
        long size = normalizeSize(sizeValue);
        long start = (page - 1) * size;
        long end = start + size - 1;
        Set<String> rankedIds = redisUtils.zReverseRange(key, start, end);
        if (rankedIds.isEmpty()) {
            return PageResult.of(List.of(), page, size, redisUtils.zSize(key));
        }
        List<Long> articleIds = rankedIds.stream().map(this::parseLong).filter(Objects::nonNull).toList();
        List<Article> articles = safeList(unwrap(contentFeignClient.listArticlesByIds(articleIds)));
        Map<Long, Article> articleMap = articles.stream().collect(Collectors.toMap(Article::getId, Function.identity(), (a, b) -> a));
        Map<Long, ArticleStatsVO> statsMap = statsMap(userId, articleIds);
        Map<Long, UserVO> userMap = userMap(articles.stream().map(Article::getUserId).filter(Objects::nonNull).toList());
        Map<Long, String> categoryNameMap = categoryNameMap();

        List<HotArticleVO> records = articleIds.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .filter(article -> Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED))
                .map(article -> toVO(article, statsMap.getOrDefault(article.getId(), emptyStats(article.getId())),
                        userMap.get(article.getUserId()), categoryNameMap.get(article.getCategoryId()), redisUtils.zScore(key, article.getId().toString())))
                .toList();
        return PageResult.of(records, page, size, redisUtils.zSize(key));
    }

    private void ensureRankReady(String key) {
        if (redisUtils.zSize(key) == 0) {
            calculateHotArticles();
        }
    }

    private void trimRank(String key) {
        long size = redisUtils.zSize(key);
        if (size > RecommendConstants.HOT_RANK_SIZE) {
            redisUtils.zRemoveRange(key, 0, size - RecommendConstants.HOT_RANK_SIZE - 1);
        }
    }

    private void removeArticle(Long articleId) {
        redisUtils.zRemove(RecommendConstants.HOT_ARTICLE_RANK_KEY, articleId.toString());
        for (Category category : enabledCategories()) {
            redisUtils.zRemove(categoryKey(category.getId()), articleId.toString());
        }
    }

    private double calculateScore(ArticleStatsVO stats) {
        return defaultLong(stats.getLikeCount()) * RecommendConstants.LIKE_WEIGHT
                + defaultLong(stats.getCommentCount()) * RecommendConstants.COMMENT_WEIGHT
                + defaultLong(stats.getCommentLikeCount()) * RecommendConstants.COMMENT_LIKE_WEIGHT
                + defaultLong(stats.getReplyCount()) * RecommendConstants.REPLY_WEIGHT
                + defaultLong(stats.getReplyLikeCount()) * RecommendConstants.REPLY_LIKE_WEIGHT
                + defaultLong(stats.getViewCount()) * RecommendConstants.VIEW_WEIGHT;
    }

    private HotArticleVO toVO(Article article, ArticleStatsVO stats, UserVO author, String categoryName, Double score) {
        HotArticleVO vo = new HotArticleVO();
        vo.setId(article.getId());
        vo.setUserId(article.getUserId());
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setCoverUrl(article.getCoverUrl());
        vo.setCategoryId(article.getCategoryId());
        vo.setCategoryName(categoryName);
        vo.setAuthorName(author == null ? "玩家" + article.getUserId() : author.getUsername());
        vo.setAuthorAvatar(author == null ? null : author.getAvatar());
        vo.setLikeCount(defaultLong(stats.getLikeCount()));
        vo.setCommentCount(defaultLong(stats.getCommentCount()));
        vo.setCommentLikeCount(defaultLong(stats.getCommentLikeCount()));
        vo.setReplyCount(defaultLong(stats.getReplyCount()));
        vo.setReplyLikeCount(defaultLong(stats.getReplyLikeCount()));
        vo.setViewCount(defaultLong(stats.getViewCount()));
        vo.setLiked(Boolean.TRUE.equals(stats.getLiked()));
        vo.setHotScore(score == null ? calculateScore(stats) : score);
        vo.setPublishedTime(article.getPublishedTime());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());
        return vo;
    }

    private Map<Long, ArticleStatsVO> statsMap(Long userId, List<Long> articleIds) {
        if (CollectionUtils.isEmpty(articleIds)) {
            return Map.of();
        }
        List<ArticleStatsVO> stats = safeList(unwrap(socialFeignClient.getStats(new ArrayList<>(new LinkedHashSet<>(articleIds)), userId)));
        return stats.stream().collect(Collectors.toMap(ArticleStatsVO::getArticleId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private Map<Long, UserVO> userMap(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Map.of();
        }
        List<UserVO> users = safeList(unwrap(userFeignClient.getUsersByIds(new ArrayList<>(new LinkedHashSet<>(userIds)))));
        return users.stream().collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
    }

    private Map<Long, String> categoryNameMap() {
        Map<Long, String> result = new HashMap<>();
        for (Category category : enabledCategories()) {
            result.put(category.getId(), category.getName());
        }
        return result;
    }

    private List<Category> enabledCategories() {
        return safeList(unwrap(contentFeignClient.listEnabledCategories()));
    }

    private String categoryKey(Long categoryId) {
        return RecommendConstants.CATEGORY_HOT_ARTICLE_RANK_KEY_PREFIX + categoryId;
    }

    private ArticleStatsVO emptyStats(Long articleId) {
        ArticleStatsVO vo = new ArticleStatsVO();
        vo.setArticleId(articleId);
        vo.setLikeCount(0L);
        vo.setCommentCount(0L);
        vo.setCommentLikeCount(0L);
        vo.setReplyCount(0L);
        vo.setReplyLikeCount(0L);
        vo.setViewCount(0L);
        vo.setLiked(false);
        return vo;
    }

    private <T> T unwrap(Result<T> result) {
        return result == null || result.getCode() == null || result.getCode() != 200 ? null : result.getData();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizeSize(Long size) {
        return size == null || size < 1 ? 12 : Math.min(size, 50);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
