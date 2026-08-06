package com.game.community.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.entity.social.SocialArticleStats;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SocialStatsCache {

    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;

    @Value("${social.stats-cache.ttl-seconds:5}")
    private long ttlSeconds;

    public SocialArticleStats get(Long articleId) {
        if (articleId == null) {
            return null;
        }
        String value;
        try {
            value = redisUtils.get(key(articleId));
        } catch (RuntimeException ignored) {
            return null;
        }
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, SocialArticleStats.class);
        } catch (JsonProcessingException e) {
            try {
                redisUtils.del(key(articleId));
            } catch (RuntimeException ignored) {
                // 缓存损坏或 Redis 暂不可用时回源数据库。
            }
            return null;
        }
    }

    public void put(SocialArticleStats stats) {
        if (stats == null || stats.getArticleId() == null) {
            return;
        }
        try {
            redisUtils.setEx(key(stats.getArticleId()), objectMapper.writeValueAsString(stats), ttlSeconds);
        } catch (JsonProcessingException | RuntimeException ignored) {
            // 缓存失败不影响 MySQL 主链路。
        }
    }

    public void evict(Long articleId) {
        if (articleId != null) {
            try {
                redisUtils.del(key(articleId));
            } catch (RuntimeException ignored) {
                // 缓存失效失败不影响数据库主链路。
            }
        }
    }

    private String key(Long articleId) {
        return "social:stats:article:" + articleId;
    }
}
