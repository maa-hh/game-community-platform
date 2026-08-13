package com.game.community.danmaku.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.dto.danmaku.SendDanmakuDTO;
import com.game.community.model.message.DanmakuEvent;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.danmaku.DanmakuVO;
import com.game.community.utils.DfaAuditUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DanmakuCommandService {

    private static final String ID_KEY = "danmaku:id";
    private static final String SEQ_PREFIX = "danmaku:seq:";
    private static final String DEDUPE_PREFIX = "danmaku:dedupe:";
    private static final String DEDUPE_PROCESSING = "__PROCESSING__";
    private static final String RECENT_PREFIX = "danmaku:recent:";

    private final DanmakuArticleValidator articleValidator;
    private final DanmakuRateLimiter rateLimiter;
    private final DfaAuditUtils dfaAuditUtils;
    private final UserFeignClient userFeignClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, DanmakuEvent> kafkaTemplate;
    private final DanmakuRealtimeService realtimeService;

    @Value("${danmaku.max-content-length:200}")
    private int maxContentLength;

    @Value("${danmaku.recent-ttl-seconds:604800}")
    private long recentTtlSeconds;

    public DanmakuVO accept(String videoPublicId, Long userId, SendDanmakuDTO dto) {
        if (userId == null) {
            throw new BusinessException("请先登录后发送弹幕");
        }
        var article = articleValidator.requireVideo(videoPublicId);
        validate(dto);
        rateLimiter.check(userId, videoPublicId);

        String dedupeKey = DEDUPE_PREFIX + userId + ":" + videoPublicId + ":" + dto.getClientMessageId();
        DanmakuVO existing = readDedupe(dedupeKey);
        if (existing != null) {
            return existing;
        }
        Boolean claimed = redis.opsForValue().setIfAbsent(
                dedupeKey, DEDUPE_PROCESSING, 30, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(claimed)) {
            throw new BusinessException("弹幕正在处理中，请稍后重试");
        }

        boolean accepted = false;
        try {
            Long id = nextId();
            Long seq = nextSeq(videoPublicId);
            UserCardInternalVO user = resolveUser(userId);
            if (user == null || user.getAccountId() == null) {
                throw new BusinessException("用户资料服务暂不可用");
            }
            String content = dto.getContent().trim();
            DanmakuEvent event = new DanmakuEvent();
            event.setId(id);
            event.setEventId(UUID.randomUUID().toString());
            event.setArticleId(article.getId());
            event.setClientMessageId(dto.getClientMessageId());
            event.setVideoPublicId(videoPublicId);
            event.setVideoTimeMs(dto.getVideoTimeMs());
            event.setDisplayTimeMs(dto.getVideoTimeMs());
            event.setSeq(seq);
            event.setAccountId(user.getAccountId());
            event.setUsernameSnapshot(user == null || !StringUtils.hasText(user.getUsername())
                    ? "玩家" : user.getUsername());
            event.setAvatarSnapshot(user == null ? null : user.getAvatar());
            event.setContent(content);
            event.setStatus(1);
            event.setEventTime(LocalDateTime.now());
            kafkaTemplate.send(KafkaTopicConstants.DANMAKU_TOPIC, videoPublicId, event)
                    .get(3, TimeUnit.SECONDS);
            accepted = true;

            DanmakuVO vo = toVO(event);
            cacheRecent(vo);
            writeDedupe(dedupeKey, vo);
            realtimeService.publish(videoPublicId, DanmakuRealtimeMessage.created(vo));
            return vo;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("弹幕发送被中断，请重试");
        } catch (Exception e) {
            throw new BusinessException("弹幕服务繁忙，请稍后重试");
        } finally {
            if (!accepted) {
                try {
                    redis.delete(dedupeKey);
                } catch (RuntimeException ignored) {
                    // 失败请求的锁会自动过期，避免二次异常覆盖原始错误。
                }
            }
        }
    }

    private void validate(SendDanmakuDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getClientMessageId())) {
            throw new BusinessException("弹幕请求无效");
        }
        if (dto.getVideoTimeMs() == null || dto.getVideoTimeMs() < 0) {
            throw new BusinessException("视频时间无效");
        }
        if (!StringUtils.hasText(dto.getContent())) {
            throw new BusinessException("弹幕内容不能为空");
        }
        if (dto.getContent().trim().length() > maxContentLength) {
            throw new BusinessException("弹幕内容过长");
        }
        if (!dfaAuditUtils.pass(dto.getContent())) {
            throw new BusinessException("弹幕包含敏感内容");
        }
    }

    private Long nextId() {
        Long value = redis.opsForValue().increment(ID_KEY);
        if (value == null) {
            throw new BusinessException("弹幕编号服务暂不可用");
        }
        return value;
    }

    private Long nextSeq(String videoPublicId) {
        Long value = redis.opsForValue().increment(SEQ_PREFIX + videoPublicId);
        if (value == null) {
            throw new BusinessException("弹幕顺序服务暂不可用");
        }
        return value;
    }

    private UserCardInternalVO resolveUser(Long userId) {
        try {
            var result = userFeignClient.getUsersByUserIds(List.of(userId));
            return result == null || result.getData() == null ? null : result.getData().stream().findFirst().orElse(null);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void cacheRecent(DanmakuVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            redis.opsForZSet().add(RECENT_PREFIX + vo.getVideoPublicId(), json, vo.getDisplayTimeMs());
            redis.opsForValue().set("danmaku:message:" + vo.getId(), json, recentTtlSeconds, TimeUnit.SECONDS);
            redis.expire(RECENT_PREFIX + vo.getVideoPublicId(), java.time.Duration.ofSeconds(recentTtlSeconds));
        } catch (JsonProcessingException | DataAccessException e) {
            // Kafka 已确认，Redis 只作为热历史和实时辅助缓存，不阻断消息接收。
        }
    }

    private DanmakuVO readDedupe(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return StringUtils.hasText(json) && !DEDUPE_PROCESSING.equals(json)
                    ? objectMapper.readValue(json, DanmakuVO.class) : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeDedupe(String key, DanmakuVO vo) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(vo), 24, TimeUnit.HOURS);
        } catch (Exception ignored) {
            // 可靠事件已进入 Kafka，幂等缓存失败不影响已接受消息。
        }
    }

    public DanmakuVO toVO(DanmakuEvent event) {
        DanmakuVO vo = new DanmakuVO();
        vo.setId(event.getId());
        vo.setEventId(event.getEventId());
        vo.setClientMessageId(event.getClientMessageId());
        vo.setVideoPublicId(event.getVideoPublicId());
        vo.setVideoTimeMs(event.getVideoTimeMs());
        vo.setDisplayTimeMs(event.getDisplayTimeMs());
        vo.setSeq(event.getSeq());
        vo.setAccountId(event.getAccountId());
        vo.setUsername(event.getUsernameSnapshot());
        vo.setAvatar(event.getAvatarSnapshot());
        vo.setContent(event.getContent());
        vo.setStatus(event.getStatus());
        return vo;
    }
}
