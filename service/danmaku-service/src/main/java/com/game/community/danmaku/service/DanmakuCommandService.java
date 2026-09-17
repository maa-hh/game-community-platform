package com.game.community.danmaku.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.danmaku.common.constant.DanmakuCacheConstants;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.dto.danmaku.SendDanmakuDTO;
import com.game.community.model.message.DanmakuEvent;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.danmaku.DanmakuVO;
import com.game.community.utils.DfaAuditUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class DanmakuCommandService {

    private final DanmakuArticleValidator articleValidator;
    private final DanmakuRateLimiter rateLimiter;
    private final DfaAuditUtils dfaAuditUtils;
    private final UserFeignClient userFeignClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, DanmakuEvent> kafkaTemplate;
    private final DanmakuViewMapper viewMapper;

    @Value("${danmaku.max-content-length:200}")
    private int maxContentLength;

    @Value("${danmaku.dedupe-processing-ttl-seconds:60}")
    private long dedupeProcessingTtlSeconds;

    /** 接收并校验发送命令，确认 Kafka 可靠投递后返回已接受的弹幕视图。 */
    public DanmakuVO accept(String videoPublicId, Long userId, SendDanmakuDTO dto) {
        if (userId == null) {
            throw new BusinessException("请先登录后发送弹幕");
        }
        var article = articleValidator.requireVideo(videoPublicId);
        validate(dto);

        String dedupeKey = DanmakuCacheConstants.DEDUPE_KEY_PREFIX + userId + ":"
                + videoPublicId + ":" + dto.getClientMessageId();
        DanmakuVO existing = readDedupe(dedupeKey);
        if (existing != null) {
            return existing;
        }
        Boolean claimed = redis.opsForValue().setIfAbsent(
                dedupeKey, DanmakuCacheConstants.DEDUPE_PROCESSING_VALUE,
                Math.max(1, dedupeProcessingTtlSeconds), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(claimed)) {
            throw new BusinessException("弹幕正在处理中，请稍后重试");
        }

        boolean dedupeFinalized = false;
        boolean deliveryPending = false;
        try {
            rateLimiter.check(userId, videoPublicId);
            UserCardInternalVO user = resolveUser(userId);
            if (user == null || user.getAccountId() == null) {
                throw new BusinessException("用户资料服务暂不可用");
            }
            Long id = nextId();
            Long seq = nextSeq(videoPublicId);
            String content = dto.getContent().trim();
            DanmakuEvent event = new DanmakuEvent();
            event.setId(id);
            event.setEventId(buildEventId(userId, videoPublicId, dto.getClientMessageId()));
            event.setArticleId(article.getId());
            event.setClientMessageId(dto.getClientMessageId());
            event.setVideoPublicId(videoPublicId);
            event.setVideoTimeMs(dto.getVideoTimeMs());
            event.setDisplayTimeMs(dto.getVideoTimeMs());
            event.setSeq(seq);
            event.setAccountId(user.getAccountId());
            event.setUsernameSnapshot(!StringUtils.hasText(user.getUsername())
                    ? "玩家" : user.getUsername());
            event.setAvatarSnapshot(user.getAvatar());
            event.setContent(content);
            event.setStatus(1);
            event.setEventTime(LocalDateTime.now());
            DanmakuVO vo = viewMapper.fromEvent(event);
            CompletableFuture<SendResult<String, DanmakuEvent>> delivery =
                    kafkaTemplate.send(KafkaTopicConstants.DANMAKU_TOPIC, videoPublicId, event);
            try {
                delivery.get(3, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                deliveryPending = true;
                finalizeDeliveryAfterTimeout(delivery, dedupeKey, vo);
                throw new BusinessException("弹幕状态确认超时，请稍后使用原消息重试");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                deliveryPending = true;
                finalizeDeliveryAfterTimeout(delivery, dedupeKey, vo);
                throw new BusinessException("弹幕发送被中断，请重试");
            }

            finalizeAccepted(dedupeKey, vo);
            dedupeFinalized = true;
            return vo;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("弹幕服务繁忙，请稍后重试");
        } finally {
            if (!dedupeFinalized && !deliveryPending) {
                releaseDedupe(dedupeKey);
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
        Long value = redis.opsForValue().increment(DanmakuCacheConstants.ID_KEY);
        if (value == null) {
            throw new BusinessException("弹幕编号服务暂不可用");
        }
        return value;
    }

    private Long nextSeq(String videoPublicId) {
        Long value = redis.opsForValue().increment(DanmakuCacheConstants.SEQ_KEY_PREFIX + videoPublicId);
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

    /** 根据客户端幂等身份生成稳定事件 ID，使重试不会重复计入下游热度。 */
    private String buildEventId(Long userId, String videoPublicId, String clientMessageId) {
        String identity = userId + ":" + videoPublicId + ":" + clientMessageId;
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private DanmakuVO readDedupe(String key) {
        try {
            String json = redis.opsForValue().get(key);
            return StringUtils.hasText(json)
                    && !DanmakuCacheConstants.DEDUPE_PROCESSING_VALUE.equals(json)
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

    /** Kafka 发送成功后只完成幂等结果；热缓存和实时广播由落库消费者完成。 */
    private void finalizeAccepted(String dedupeKey, DanmakuVO vo) {
        writeDedupe(dedupeKey, vo);
    }

    /** Kafka 发送超时后继续等待最终结果，避免客户端重试产生第二条可靠事件。 */
    private void finalizeDeliveryAfterTimeout(
            CompletableFuture<SendResult<String, DanmakuEvent>> delivery,
            String dedupeKey, DanmakuVO vo) {
        delivery.orTimeout(Math.max(1, dedupeProcessingTtlSeconds), TimeUnit.SECONDS)
                .whenComplete((ignored, error) -> {
                    if (error == null) {
                        finalizeAccepted(dedupeKey, vo);
                    } else {
                        releaseDedupe(dedupeKey);
                    }
                });
    }

    /** 释放未进入 Kafka 的幂等占位，允许客户端使用同一 clientMessageId 重试。 */
    private void releaseDedupe(String dedupeKey) {
        try {
            redis.delete(dedupeKey);
        } catch (RuntimeException ignored) {
            // 占位会自动过期，不能让清理异常覆盖原始发送结果。
        }
    }
}
