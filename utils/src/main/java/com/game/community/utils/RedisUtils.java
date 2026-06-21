package com.game.community.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 字符串工具
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private final StringRedisTemplate stringRedisTemplate;

    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, String value, long timeout, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public void setEx(String key, String value, long seconds) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(seconds));
    }

    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    public Boolean setIfAbsent(String key, String value, long seconds) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(seconds));
    }

    public void del(String key) {
        stringRedisTemplate.delete(key);
    }

    public Long listPush(String key, String value) {
        return stringRedisTemplate.opsForList().leftPush(key, value);
    }

    public Long listPushRight(String key, String value) {
        return stringRedisTemplate.opsForList().rightPush(key, value);
    }

    public String listLeftPop(String key) {
        return stringRedisTemplate.opsForList().leftPop(key);
    }

    public Long listRemove(String key, String value) {
        return stringRedisTemplate.opsForList().remove(key, 0, value);
    }

    public Boolean zAdd(String key, String value, double score) {
        return stringRedisTemplate.opsForZSet().add(key, value, score);
    }

    public Long zRemove(String key, String value) {
        return stringRedisTemplate.opsForZSet().remove(key, value);
    }

    public Long zRemoveRange(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().removeRange(key, start, end);
    }

    public Set<String> zReverseRange(String key, long start, long end) {
        Set<String> result = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return result == null ? Set.of() : new LinkedHashSet<>(result);
    }

    public Long zSize(String key) {
        Long result = stringRedisTemplate.opsForZSet().size(key);
        return result == null ? 0L : result;
    }

    public Double zScore(String key, String value) {
        return stringRedisTemplate.opsForZSet().score(key, value);
    }

    public Set<String> zRangeByScore(String key, double min, double max) {
        Set<String> result = stringRedisTemplate.opsForZSet().rangeByScore(key, min, max);
        return result == null ? Set.of() : result;
    }

    public Set<String> setMembers(String key) {
        Set<String> result = stringRedisTemplate.opsForSet().members(key);
        return result == null ? Set.of() : new LinkedHashSet<>(result);
    }
}
