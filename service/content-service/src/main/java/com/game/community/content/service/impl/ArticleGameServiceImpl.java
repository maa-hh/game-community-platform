package com.game.community.content.service.impl;

import com.game.community.content.mapper.ArticleGameMapper;
import com.game.community.content.service.ArticleGameService;
import com.game.community.feign.SearchFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.entity.game.ArticleGame;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.game.GameTagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.game.community.utils.RedisUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.game.community.content.service.ContentOutboxService;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleGameServiceImpl implements ArticleGameService {

    private final ArticleGameMapper articleGameMapper;
    private final SearchFeignClient searchFeignClient;
    private final RedisUtils redisUtils;

    private final ContentOutboxService contentOutboxService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveArticleGames(Long articleId, List<Long> appIds) {
        if (articleId == null) {
            return;
        }
        Set<Long> previous = new LinkedHashSet<>(articleGameMapper.selectAppIdsByArticleId(articleId));
        Set<Long> unique = new LinkedHashSet<>();
        for (Long appId : appIds == null ? List.<Long>of() : appIds) {
            if (appId != null) {
                unique.add(appId);
            }
        }
        Set<Long> removed = new LinkedHashSet<>(previous);
        removed.removeAll(unique);
        if (unique.isEmpty()) {
            articleGameMapper.deleteByArticleId(articleId);
        } else if (!removed.isEmpty()) {
            articleGameMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ArticleGame>()
                    .eq(ArticleGame::getArticleId, articleId)
                    .in(ArticleGame::getGameAppId, removed));
        }
        LocalDateTime now = LocalDateTime.now();
        for (Long appId : unique) {
            if (previous.contains(appId)) {
                continue;
            }
            ArticleGame relation = new ArticleGame();
            relation.setArticleId(articleId);
            relation.setGameAppId(appId);
            relation.setCreateTime(now);
            articleGameMapper.insert(relation);
        }
        Set<Long> affectedAppIds = new LinkedHashSet<>(previous);
        affectedAppIds.addAll(unique);
        scheduleDiscussCountSync(affectedAppIds);
    }

    private void scheduleDiscussCountSync(Set<Long> appIds) {
        if (CollectionUtils.isEmpty(appIds)) {
            return;
        }
        Runnable action = () -> contentOutboxService.enqueue(
                "steam-discuss-sync:" + UUID.randomUUID(),
                "GAME_DISCUSS_SYNC", null, "steam-discuss-count",
                Map.of("appIds", new ArrayList<>(appIds)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Override
    public List<GameTagVO> listByArticle(Long articleId) {
        if (articleId == null) {
            return List.of();
        }
        return loadTags(articleGameMapper.selectAppIdsByArticleId(articleId));
    }

    @Override
    public Map<Long, List<GameTagVO>> listByArticles(List<Long> articleIds) {
        if (CollectionUtils.isEmpty(articleIds)) {
            return Map.of();
        }
        List<ArticleGame> relations = articleGameMapper.selectByArticleIds(articleIds);
        Map<Long, List<Long>> appIdsByArticle = new HashMap<>();
        Set<Long> allAppIds = new LinkedHashSet<>();
        for (ArticleGame relation : relations) {
            appIdsByArticle.computeIfAbsent(relation.getArticleId(), ignored -> new ArrayList<>())
                    .add(relation.getGameAppId());
            allAppIds.add(relation.getGameAppId());
        }
        Map<Long, GameTagVO> tagsByAppId = loadTags(allAppIds.stream().toList()).stream()
                .filter(tag -> tag.getAppId() != null)
                .collect(Collectors.toMap(GameTagVO::getAppId, tag -> tag, (left, right) -> left));
        Map<Long, List<GameTagVO>> result = new HashMap<>();
        for (Long articleId : articleIds) {
            result.put(articleId, appIdsByArticle.getOrDefault(articleId, List.of()).stream()
                    .map(tagsByAppId::get)
                    .filter(Objects::nonNull)
                    .toList());
        }
        return result;
    }

    @Override
    public void enrichListVOs(List<ArticleListVO> articles) {
        if (CollectionUtils.isEmpty(articles)) {
            return;
        }
        List<Long> articleIds = articles.stream()
                .map(ArticleListVO::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, List<GameTagVO>> tagMap = listByArticles(articleIds);
        for (ArticleListVO article : articles) {
            article.setGameTags(tagMap.getOrDefault(article.getId(), List.of()));
        }
    }

    private List<GameTagVO> loadTags(List<Long> appIds) {
        if (CollectionUtils.isEmpty(appIds)) {
            return List.of();
        }
        List<String> keys = appIds.stream().map(this::gameTagCacheKey).toList();
        List<String> cached;
        try {
            cached = redisUtils.multiGet(keys.toArray(String[]::new));
        } catch (Exception ignored) {
            // Redis 故障只影响缓存命中，不阻断帖子列表回源。
            cached = List.of();
        }
        Map<Long, GameTagVO> tagMap = new HashMap<>();
        List<Long> missing = new ArrayList<>();
        for (int i = 0; i < appIds.size(); i++) {
            String raw = cached.size() > i ? cached.get(i) : null;
            if (StringUtils.hasText(raw)) {
                try {
                    GameTagVO tag = com.alibaba.fastjson2.JSON.parseObject(raw, GameTagVO.class);
                    if (tag != null) {
                        tagMap.put(appIds.get(i), tag);
                        continue;
                    }
                } catch (Exception ignored) {
                    // 缓存损坏时回源刷新。
                }
            }
            missing.add(appIds.get(i));
        }
        try {
            Result<List<GameTagVO>> result = missing.isEmpty() ? Result.success(List.of())
                    : searchFeignClient.listGameTags(missing);
            if (result != null && result.getData() != null) {
                Map<Long, GameTagVO> remoteTags = result.getData().stream()
                        .filter(tag -> tag.getAppId() != null)
                        .collect(Collectors.toMap(GameTagVO::getAppId, tag -> tag, (a, b) -> a));
                tagMap.putAll(remoteTags);
                for (Map.Entry<Long, GameTagVO> entry : remoteTags.entrySet()) {
                    redisUtils.set(gameTagCacheKey(entry.getKey()),
                            com.alibaba.fastjson2.JSON.toJSONString(entry.getValue()), 300,
                            java.util.concurrent.TimeUnit.SECONDS);
                }
            }
        } catch (Exception ignored) {
            // steam-service 未就绪时回退最小标签
        }
        return appIds.stream().map(appId -> tagMap.computeIfAbsent(appId, key -> {
            GameTagVO tag = new GameTagVO();
            tag.setAppId(key);
            return tag;
        })).toList();
    }

    private String gameTagCacheKey(Long appId) {
        return "content:game-tag:" + appId;
    }
}
