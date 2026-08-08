package com.game.community.danmaku.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.exception.BusinessException;
import com.game.community.danmaku.mapper.DanmakuMessageMapper;
import com.game.community.model.entity.danmaku.DanmakuMessage;
import com.game.community.model.vo.danmaku.DanmakuVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DanmakuQueryService {

    private static final String RECENT_PREFIX = "danmaku:recent:";
    private static final String MESSAGE_PREFIX = "danmaku:message:";

    private final DanmakuArticleValidator articleValidator;
    private final DanmakuMessageMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DanmakuRealtimeService realtimeService;

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
                RECENT_PREFIX + videoPublicId, from, to, 0, max);
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
            String json = redis.opsForValue().get(MESSAGE_PREFIX + messageId);
            if (json != null) {
                return objectMapper.readValue(json, DanmakuVO.class);
            }
        } catch (Exception ignored) {
            // fallback to MySQL
        }
        DanmakuMessage entity = mapper.selectById(messageId);
        return entity == null ? null : toVO(entity);
    }

    public void hide(Long messageId) {
        DanmakuVO cached = get(messageId);
        DanmakuMessage entity = mapper.selectById(messageId);
        if (entity == null) {
            if (cached == null) {
                throw new BusinessException("弹幕不存在");
            }
            evictCache(cached);
            realtimeService.publish(cached.getVideoPublicId(), DanmakuRealtimeMessage.removed(messageId));
            return;
        }
        mapper.update(null, new LambdaUpdateWrapper<DanmakuMessage>()
                .eq(DanmakuMessage::getId, messageId)
                .eq(DanmakuMessage::getStatus, 1)
                .set(DanmakuMessage::getStatus, 2));
        DanmakuVO removed = cached == null ? toVO(entity) : cached;
        evictCache(removed);
        realtimeService.publish(removed.getVideoPublicId(), DanmakuRealtimeMessage.removed(messageId));
    }

    private void evictCache(DanmakuVO vo) {
        try {
            String json = objectMapper.writeValueAsString(vo);
            redis.opsForZSet().remove(RECENT_PREFIX + vo.getVideoPublicId(), json);
            redis.delete(MESSAGE_PREFIX + vo.getId());
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
