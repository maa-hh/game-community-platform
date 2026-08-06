package com.game.community.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 字符串工具
 */
@Component
@RequiredArgsConstructor
public class RedisUtils {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Long> COMPARE_SET_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3]); return 1 "
                    + "else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Double> Z_INCREMENT_AND_TRIM_SCRIPT = new DefaultRedisScript<>(
            "local score = redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2]); "
                    + "if tonumber(score) <= 0 then "
                    + "redis.call('ZREM', KEYS[1], ARGV[2]); "
                    + "else "
                    + "local size = redis.call('ZCARD', KEYS[1]); "
                    + "local limit = tonumber(ARGV[3]); "
                    + "if size > limit then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, size - limit - 1); end; "
                    + "end; "
                    + "return score",
            Double.class);

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

    /**
     * 批量 GET（Redis MGET），一次网络往返；返回值与 keys 同序，不存在为 null。
     */
    public List<String> multiGet(String... keys) {
        if (keys == null || keys.length == 0) {
            return List.of();
        }
        List<String> values = stringRedisTemplate.opsForValue().multiGet(Arrays.asList(keys));
        if (values == null) {
            return new ArrayList<>(Collections.nCopies(keys.length, null));
        }
        return values;
    }

    /**
     * 通过 pipeline 批量 GET，一次网络往返；返回值与 keys 同序，不存在为 null。
     * <p>
     * 多个独立 GET 时优先用 {@link #multiGet(String...)}；需要与同批写命令打包时用本方法。
     */
    public List<String> pipelineGet(String... keys) {
        if (keys == null || keys.length == 0) {
            return List.of();
        }
        List<Object> raw = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            var commands = connection.stringCommands();
            for (String key : keys) {
                commands.get(key.getBytes(StandardCharsets.UTF_8));
            }
            return null;
        });
        List<String> result = new ArrayList<>(keys.length);
        for (Object item : raw) {
            if (item == null) {
                result.add(null);
            } else if (item instanceof byte[] bytes) {
                result.add(new String(bytes, StandardCharsets.UTF_8));
            } else {
                result.add(String.valueOf(item));
            }
        }
        return result;
    }

    public Boolean setIfAbsent(String key, String value, long seconds) {
        return stringRedisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(seconds));
    }

    /** 仅删除当前持有者创建的锁，避免误删其他实例的新锁。 */
    public boolean unlock(String key, String token) {
        Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(key), token);
        return result != null && result == 1L;
    }

    /** 对 Redis 字符串执行 CAS，适合 JSON 会话的并发更新。 */
    public boolean compareAndSet(String key, String expected, String value, long seconds) {
        Long result = stringRedisTemplate.execute(COMPARE_SET_SCRIPT,
                Collections.singletonList(key), expected, value, String.valueOf(seconds));
        return result != null && result == 1L;
    }

    public Long getExpireSeconds(String key) {
        Long seconds = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        return seconds == null ? -2L : seconds;
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

    public Long zRemoveRangeByScore(String key, double min, double max) {
        return stringRedisTemplate.opsForZSet().removeRangeByScore(key, min, max);
    }

    public Set<String> zReverseRange(String key, long start, long end) {
        Set<String> result = stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
        return result == null ? Set.of() : new LinkedHashSet<>(result);
    }

    /** 一次读取有序集合成员及分数，避免榜单组装阶段逐条 zScore。 */
    public Map<String, Double> zReverseRangeWithScores(String key, long start, long end) {
        Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);
        if (tuples == null || tuples.isEmpty()) {
            return Map.of();
        }
        Map<String, Double> result = new LinkedHashMap<>();
        for (var tuple : tuples) {
            if (tuple.getValue() != null) {
                result.put(tuple.getValue(), tuple.getScore());
            }
        }
        return result;
    }

    public Long zSize(String key) {
        Long result = stringRedisTemplate.opsForZSet().size(key);
        return result == null ? 0L : result;
    }

    public Double zScore(String key, String value) {
        return stringRedisTemplate.opsForZSet().score(key, value);
    }

    public Double zIncrementScore(String key, String value, double delta) {
        return stringRedisTemplate.opsForZSet().incrementScore(key, value, delta);
    }

    /** 原子地累加热度并裁剪低分成员，避免并发实例互相覆盖裁剪结果。 */
    public Double zIncrementScoreAndTrim(String key, String value, double delta, long limit) {
        return stringRedisTemplate.execute(
                Z_INCREMENT_AND_TRIM_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(delta), value, String.valueOf(limit));
    }

    public Set<String> zRangeByScore(String key, double min, double max) {
        Set<String> result = stringRedisTemplate.opsForZSet().rangeByScore(key, min, max);
        return result == null ? Set.of() : result;
    }

    public Set<String> setMembers(String key) {
        Set<String> result = stringRedisTemplate.opsForSet().members(key);
        return result == null ? Set.of() : new LinkedHashSet<>(result);
    }

    public Long setAdd(String key, String... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        Long result = stringRedisTemplate.opsForSet().add(key, values);
        return result == null ? 0L : result;
    }

    public Long setRemove(String key, String... values) {
        if (values == null || values.length == 0) {
            return 0L;
        }
        Long result = stringRedisTemplate.opsForSet().remove(key, (Object[]) values);
        return result == null ? 0L : result;
    }

    public Boolean expire(String key, long seconds) {
        return stringRedisTemplate.expire(key, Duration.ofSeconds(seconds));
    }
}
