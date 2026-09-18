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

    private static final DefaultRedisScript<Long> ADD_SET_MEMBER_IF_VALUE_MATCHES_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('sadd', KEYS[2], ARGV[2]); "
                    + "redis.call('expire', KEYS[2], ARGV[3]); return 1 "
                    + "else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Long> DELETE_IF_VALUE_MATCHES_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Long> SAVE_SESSION_SCRIPT = new DefaultRedisScript<>(
            "redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[3]); "
                    + "redis.call('set', KEYS[2], '1', 'EX', ARGV[3]); "
                    + "redis.call('set', KEYS[3], ARGV[2], 'EX', ARGV[3]); return 1",
            Long.class);

    private static final DefaultRedisScript<Long> INVALIDATE_SESSION_SCRIPT = new DefaultRedisScript<>(
            "redis.call('del', KEYS[1]); redis.call('del', KEYS[2]); "
                    + "if redis.call('get', KEYS[3]) == ARGV[1] then redis.call('del', KEYS[3]); end; return 1",
            Long.class);

    private static final DefaultRedisScript<Long> INVALIDATE_SESSION_WITHOUT_USER_SCRIPT = new DefaultRedisScript<>(
            "redis.call('del', KEYS[1]); redis.call('del', KEYS[2]); return 1",
            Long.class);

    private static final DefaultRedisScript<Long> RESERVE_VERIFY_CODE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[1]) == 1 then return 0 end; "
                    + "local count = tonumber(redis.call('get', KEYS[2]) or '0'); "
                    + "if count >= tonumber(ARGV[5]) then return 2 end; "
                    + "redis.call('set', KEYS[3], ARGV[1], 'EX', ARGV[2]); "
                    + "redis.call('set', KEYS[1], '1', 'EX', ARGV[3]); "
                    + "redis.call('set', KEYS[2], tostring(count + 1), 'EX', ARGV[4]); "
                    + "redis.call('del', KEYS[4]); return 1",
            Long.class);

    private static final DefaultRedisScript<Long> RECORD_VERIFY_FAILURE_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('incr', KEYS[1]); "
                    + "if count == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; "
                    + "if count >= tonumber(ARGV[2]) then "
                    + "redis.call('set', KEYS[2], '1', 'EX', ARGV[3]); "
                    + "redis.call('del', KEYS[1]); end; return count",
            Long.class);

    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) ~= ARGV[1] then return 0 end; "
                    + "if ARGV[2] == '1' then redis.call('del', KEYS[1]); end; return 1",
            Long.class);

    private static final DefaultRedisScript<Long> RELEASE_VERIFY_CODE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[2]) ~= ARGV[1] then return 0 end; "
                    + "redis.call('del', KEYS[1]); redis.call('del', KEYS[2]); return 1",
            Long.class);

    private static final DefaultRedisScript<String> GET_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('get', KEYS[1]); "
                    + "if value then redis.call('del', KEYS[1]); end; "
                    + "return value",
            String.class);

    private static final DefaultRedisScript<Long> RESERVE_RATE_LIMIT_SLOT_SCRIPT = new DefaultRedisScript<>(
            "local serverTime = redis.call('TIME'); "
                    + "local nowMs = tonumber(serverTime[1]) * 1000 "
                    + "+ math.floor(tonumber(serverTime[2]) / 1000); "
                    + "local previous = tonumber(redis.call('get', KEYS[1]) or '0'); "
                    + "local scheduled = math.max(nowMs, previous + tonumber(ARGV[1])); "
                    + "redis.call('set', KEYS[1], tostring(scheduled)); "
                    + "return scheduled - nowMs",
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

    private static final DefaultRedisScript<Long> Z_INCREMENT_IF_UNLOCKED_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('exists', KEYS[2]) == 1 then return 0 end; "
                    + "local score = redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2]); "
                    + "if tonumber(score) <= 0 then "
                    + "redis.call('ZREM', KEYS[1], ARGV[2]); "
                    + "else "
                    + "local size = redis.call('ZCARD', KEYS[1]); "
                    + "local limit = tonumber(ARGV[3]); "
                    + "if size > limit then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, size - limit - 1); end; "
                    + "end; return 1",
            Long.class);

    private static final DefaultRedisScript<Long> Z_SET_SCORE_AND_TRIM_SCRIPT = new DefaultRedisScript<>(
            "if tonumber(ARGV[1]) <= 0 then redis.call('ZREM', KEYS[1], ARGV[2]); return 1 end; "
                    + "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2]); "
                    + "local size = redis.call('ZCARD', KEYS[1]); "
                    + "local limit = tonumber(ARGV[3]); "
                    + "if size > limit then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, size - limit - 1); end; return 1",
            Long.class);

    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('expire', KEYS[1], ARGV[2]) else return 0 end",
            Long.class);

    private static final DefaultRedisScript<Long> Z_REPLACE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('DEL', KEYS[1]); "
                    + "local limit = tonumber(ARGV[1]); "
                    + "for i = 2, #ARGV, 2 do redis.call('ZADD', KEYS[1], ARGV[i], ARGV[i + 1]); end; "
                    + "local size = redis.call('ZCARD', KEYS[1]); "
                    + "if size > limit then redis.call('ZREMRANGEBYRANK', KEYS[1], 0, size - limit - 1); end; "
                    + "return redis.call('ZCARD', KEYS[1])",
            Long.class);

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

    /** 原子读取并删除一次性 Redis 值，避免并发请求重复消费同一状态。 */
    public String getAndDelete(String key) {
        return stringRedisTemplate.execute(
                GET_AND_DELETE_SCRIPT,
                Collections.singletonList(key));
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

    /**
     * 原子预定一个限流时间槽，返回当前请求还需要等待的毫秒数。
     * Redis 服务端时间让多个应用实例共享同一时钟和时间序列。
     */
    public long reserveRateLimitSlot(String key, long intervalMs) {
        Long waitMs = stringRedisTemplate.execute(
                RESERVE_RATE_LIMIT_SLOT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(Math.max(0L, intervalMs)));
        return waitMs == null ? 0L : Math.max(0L, waitMs);
    }

    /** 仅删除当前持有者创建的锁，避免误删其他实例的新锁。 */
    public boolean unlock(String key, String token) {
        Long result = stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(key), token);
        return result != null && result == 1L;
    }

    /** 仅当锁仍属于当前持有者时续租，避免误续租其他实例的锁。 */
    public boolean renewLock(String key, String token, long seconds) {
        Long result = stringRedisTemplate.execute(RENEW_LOCK_SCRIPT,
                Collections.singletonList(key), token, String.valueOf(seconds));
        return result != null && result == 1L;
    }

    /** 对 Redis 字符串执行 CAS，适合 JSON 会话的并发更新。 */
    public boolean compareAndSet(String key, String expected, String value, long seconds) {
        Long result = stringRedisTemplate.execute(COMPARE_SET_SCRIPT,
                Collections.singletonList(key), expected, value, String.valueOf(seconds));
        return result != null && result == 1L;
    }

    /** 仅当会话仍是指定快照时原子记录分片，避免中止/合并与上传完成回写交叉。 */
    public boolean addSetMemberIfValueMatches(String valueKey, String setKey, String expectedValue,
                                               String member, long seconds) {
        Long result = stringRedisTemplate.execute(
                ADD_SET_MEMBER_IF_VALUE_MATCHES_SCRIPT,
                Arrays.asList(valueKey, setKey),
                expectedValue, member, String.valueOf(seconds));
        return result != null && result == 1L;
    }

    /** 仅当 Redis 值仍属于当前会话时删除，避免旧会话清理误删新会话索引。 */
    public boolean deleteIfValueMatches(String key, String expectedValue) {
        Long result = stringRedisTemplate.execute(
                DELETE_IF_VALUE_MATCHES_SCRIPT,
                Collections.singletonList(key), expectedValue);
        return result != null && result == 1L;
    }

    /** 一次 Lua 执行写入会话正文、活跃标记和用户当前会话指针。 */
    public boolean saveSession(String sessionKey, String activeKey, String userActiveKey,
                               String sessionJson, String sessionId, long seconds) {
        Long result = stringRedisTemplate.execute(
                SAVE_SESSION_SCRIPT,
                Arrays.asList(sessionKey, activeKey, userActiveKey),
                sessionJson, sessionId, String.valueOf(seconds));
        return result != null && result == 1L;
    }

    /**
     * 原子删除会话正文、活跃标记，并且仅在用户指针仍指向本会话时删除用户指针。
     * 这样并发登录创建的新会话不会被旧会话的清理动作误删。
     */
    public boolean invalidateSession(String sessionKey, String activeKey,
                                     String userActiveKey, String sessionId) {
        Long result = stringRedisTemplate.execute(
                INVALIDATE_SESSION_SCRIPT,
                Arrays.asList(sessionKey, activeKey, userActiveKey),
                sessionId);
        return result != null && result == 1L;
    }

    /** 原子删除没有用户指针参与的会话键。 */
    public boolean invalidateSession(String sessionKey, String activeKey) {
        Long result = stringRedisTemplate.execute(
                INVALIDATE_SESSION_WITHOUT_USER_SCRIPT,
                Arrays.asList(sessionKey, activeKey));
        return result != null && result == 1L;
    }

    public long reserveVerificationCode(String cooldownKey, String dailyKey, String codeKey,
                                        String failureKey, String code, long codeSeconds,
                                        long cooldownSeconds, long dailySeconds, int dailyLimit) {
        Long result = stringRedisTemplate.execute(
                RESERVE_VERIFY_CODE_SCRIPT,
                Arrays.asList(cooldownKey, dailyKey, codeKey, failureKey),
                code, String.valueOf(codeSeconds), String.valueOf(cooldownSeconds),
                String.valueOf(dailySeconds), String.valueOf(dailyLimit));
        return result == null ? -1L : result;
    }

    public long recordVerificationFailure(String failureKey, String lockKey,
                                          long failureSeconds, int threshold, long lockSeconds) {
        Long result = stringRedisTemplate.execute(
                RECORD_VERIFY_FAILURE_SCRIPT,
                Arrays.asList(failureKey, lockKey),
                String.valueOf(failureSeconds), String.valueOf(threshold), String.valueOf(lockSeconds));
        return result == null ? -1L : result;
    }

    /** 原子校验验证码，并按需消费，避免并发请求重复使用同一个验证码。 */
    public boolean verifyCode(String key, String expected, boolean consume) {
        Long result = stringRedisTemplate.execute(VERIFY_CODE_SCRIPT,
                Collections.singletonList(key), expected == null ? "" : expected, consume ? "1" : "0");
        return result != null && result == 1L;
    }

    /** 邮件任务入队失败时，仅释放本次验证码和冷却，避免队列背压导致用户被无故锁等待。 */
    public boolean releaseVerificationCode(String cooldownKey, String codeKey, String code) {
        Long result = stringRedisTemplate.execute(RELEASE_VERIFY_CODE_SCRIPT,
                Arrays.asList(cooldownKey, codeKey), code == null ? "" : code);
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

    /** 维护锁存在时拒绝实时增量，防止重建的全量替换被并发事件覆盖。 */
    public boolean zIncrementScoreAndTrimUnlessLocked(String key, String lockKey,
                                                       String value, double delta, long limit) {
        Long result = stringRedisTemplate.execute(
                Z_INCREMENT_IF_UNLOCKED_SCRIPT,
                Arrays.asList(key, lockKey),
                String.valueOf(delta), value, String.valueOf(limit));
        return result != null && result == 1L;
    }

    /** 原子设置一个热榜成员的精确分数并裁剪低分成员。 */
    public boolean zSetScoreAndTrim(String key, String value, double score, long limit) {
        Long result = stringRedisTemplate.execute(
                Z_SET_SCORE_AND_TRIM_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(score), value, String.valueOf(limit));
        return result != null && result == 1L;
    }

    /** 原子替换一个热榜 Sorted Set，避免重建期间出现空榜或半榜。 */
    public long zReplace(String key, Map<String, Double> scores, long limit) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(limit));
        if (scores != null) {
            scores.forEach((member, score) -> {
                if (member != null && score != null && score > 0D) {
                    args.add(String.valueOf(score));
                    args.add(member);
                }
            });
        }
        Object[] scriptArgs = args.toArray();
        Long result = stringRedisTemplate.execute(Z_REPLACE_SCRIPT,
                Collections.singletonList(key), scriptArgs);
        return result == null ? 0L : result;
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

    public boolean setContains(String key, String value) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForSet().isMember(key, value));
    }

    public Boolean expire(String key, long seconds) {
        return stringRedisTemplate.expire(key, Duration.ofSeconds(seconds));
    }
}
