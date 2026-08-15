package com.game.community.danmaku.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.danmaku.common.constant.DanmakuCacheConstants;
import com.game.community.danmaku.mapper.DanmakuMessageMapper;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.entity.danmaku.DanmakuMessage;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.model.vo.danmaku.DanmakuVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DanmakuQueryService {

    private final DanmakuArticleValidator articleValidator;
    private final ContentFeignClient contentFeignClient;
    private final DanmakuMessageMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DanmakuRealtimeService realtimeService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${danmaku.history-window-ms:30000}")
    private long maxWindowMs;

    public List<DanmakuVO> history(String videoPublicId, Long fromMs, Long toMs, Integer limit) {
        articleValidator.requireVideo(videoPublicId);
        long from = Math.max(0, fromMs == null ? 0 : fromMs);
        long to = toMs == null ? from + maxWindowMs : toMs;
        if (to <= from) {
            throw new BusinessException("弹幕时间范围无效");
        }
        if (to - from > maxWindowMs) {
            to = from + maxWindowMs;
        }
        int max = limit == null ? 300 : Math.min(Math.max(limit, 1), 500);
        Map<Long, DanmakuVO> merged = new LinkedHashMap<>();

        Set<String> cached = redis.opsForZSet().rangeByScore(
                DanmakuCacheConstants.RECENT_KEY_PREFIX + videoPublicId, from, to, 0, max);
        if (cached != null) {
            for (String json : cached) {
                try {
                    DanmakuVO vo = objectMapper.readValue(json, DanmakuVO.class);
                    if (vo.getId() != null && Integer.valueOf(1).equals(vo.getStatus())) {
                        merged.put(vo.getId(), vo);
                    }
                } catch (Exception ignored) {
                    // 单条缓存损坏不影响其他弹幕。
                }
            }
        }

        if (merged.size() < max) {
            mapper.selectHistory(videoPublicId, from, to, max).stream()
                    .map(this::toVO)
                    .forEach(vo -> merged.putIfAbsent(vo.getId(), vo));
        }

        return merged.values().stream()
                .filter(vo -> Integer.valueOf(1).equals(vo.getStatus()))
                .sorted(Comparator.comparing(DanmakuVO::getDisplayTimeMs)
                        .thenComparing(DanmakuVO::getSeq))
                .limit(max)
                .toList();
    }

    public DanmakuVO get(Long messageId) {
        if (messageId == null) {
            return null;
        }
        try {
            String json = redis.opsForValue().get(DanmakuCacheConstants.MESSAGE_KEY_PREFIX + messageId);
            if (json != null) {
                return objectMapper.readValue(json, DanmakuVO.class);
            }
        } catch (Exception ignored) {
            // fallback to MySQL
        }
        DanmakuMessage entity = mapper.selectById(messageId);
        return entity == null ? null : toVO(entity);
    }

    /** 按视频公开 ID 校验归属，只返回仍处于可见状态的弹幕。 */
    public DanmakuVO getVisible(String videoPublicId, Long messageId) {
        articleValidator.requireVideo(videoPublicId);
        DanmakuVO message = get(messageId);
        if (message == null
                || !videoPublicId.equals(message.getVideoPublicId())
                || !Integer.valueOf(1).equals(message.getStatus())) {
            return null;
        }
        return message;
    }

    /** 持久化隐藏状态并发布实时移除及热度扣减事件。 */
    public void hide(Long messageId) {
        DanmakuVO cached = get(messageId);
        DanmakuMessage entity = mapper.selectById(messageId);
        if (entity == null) {
            throw new BusinessException(cached == null
                    ? "弹幕不存在" : "弹幕尚未完成落库，请稍后重试");
        }
        int updated = mapper.update(null, new LambdaUpdateWrapper<DanmakuMessage>()
                .eq(DanmakuMessage::getId, messageId)
                .eq(DanmakuMessage::getStatus, 1)
                .set(DanmakuMessage::getStatus, 2));
        if (updated > 0) {
            publishHeatRemoval(entity);
        }
        DanmakuVO removed = cached == null ? toVO(entity) : cached;
        evictCache(removed);
        realtimeService.publish(removed.getVideoPublicId(), DanmakuRealtimeMessage.removed(messageId));
    }

    /** 弹幕被隐藏后发送一次幂等负向行为，避免已经计入热榜的热度永久残留。 */
    private void publishHeatRemoval(DanmakuMessage entity) {
        Long articleId;
        try {
            articleId = resolveArticleId(entity.getVideoPublicId());
        } catch (RuntimeException e) {
            log.warn("弹幕热度扣减事件无法解析文章，等待回填校正: danmakuId={}", entity.getId(), e);
            return;
        }
        ArticleBehaviorMessage behavior = new ArticleBehaviorMessage();
        behavior.setEventId("danmaku-hide:" + entity.getEventId());
        behavior.setArticleId(articleId);
        behavior.setDanmakuDelta(-1L);
        behavior.setEventTimeMs(System.currentTimeMillis());
        kafkaTemplate.send(KafkaTopicConstants.ARTICLE_BEHAVIOR_TOPIC,
                        String.valueOf(behavior.getArticleId()), behavior)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        // 当前状态表和 XXL-JOB 回填仍是最终校正来源，不能阻断审核结果。
                        // 负向事件失败时由日志告警，避免把 Kafka 外部 IO 伪装成数据库事务。
                        log.warn("弹幕热度扣减事件投递失败: danmakuId={}, eventId={}",
                                entity.getId(), behavior.getEventId(), error);
                    }
                });
    }

    /** 通过视频公开 ID 获取内部文章 ID，内部事件边界不向公开接口暴露该字段。 */
    private Long resolveArticleId(String videoPublicId) {
        var result = contentFeignClient.getArticleByPublicId(videoPublicId);
        if (result == null || result.getData() == null || result.getData().getId() == null) {
            throw new BusinessException("视频帖子不存在");
        }
        return result.getData().getId();
    }

    private void evictCache(DanmakuVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            redis.opsForZSet().remove(DanmakuCacheConstants.RECENT_KEY_PREFIX + vo.getVideoPublicId(), json);
            redis.delete(DanmakuCacheConstants.MESSAGE_KEY_PREFIX + vo.getId());
        } catch (Exception ignored) {
            // 数据库状态已更新，缓存清理失败时由 TTL 兜底，不影响审核结果。
        }
    }

    private DanmakuVO toVO(DanmakuMessage entity) {
        DanmakuVO vo = new DanmakuVO();
        vo.setId(entity.getId());
        vo.setEventId(entity.getEventId());
        vo.setClientMessageId(entity.getClientMessageId());
        vo.setVideoPublicId(entity.getVideoPublicId());
        vo.setVideoTimeMs(entity.getVideoTimeMs());
        vo.setDisplayTimeMs(entity.getDisplayTimeMs());
        vo.setSeq(entity.getSeq());
        // 弹幕响应只暴露 accountId；内部事件/表仍使用 userId 关联。
        vo.setAccountId(entity.getAccountId());
        vo.setUsername(entity.getUsernameSnapshot());
        vo.setAvatar(entity.getAvatarSnapshot());
        vo.setContent(entity.getContent());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
