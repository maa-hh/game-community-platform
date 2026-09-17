package com.game.community.danmaku.service;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.danmaku.mapper.DanmakuMessageMapper;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.entity.danmaku.DanmakuMessage;
import com.game.community.model.message.DanmakuEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuPersistenceListener {

    private final DanmakuMessageMapper mapper;
    private final UserFeignClient userFeignClient;
    private final DanmakuRealtimeService realtimeService;

    /** 消费可靠弹幕事件并按数据库唯一键完成至少一次投递下的幂等落库。 */
    @KafkaListener(topics = KafkaTopicConstants.DANMAKU_TOPIC, groupId = "${spring.kafka.consumer.group-id:danmaku-persistence}")
    public void persist(DanmakuEvent event) {
        if (event == null || event.getId() == null || event.getEventId() == null
                || event.getEventId().isBlank() || event.getAccountId() == null) {
            return;
        }
        DanmakuMessage entity = new DanmakuMessage();
        entity.setId(event.getId());
        entity.setEventId(event.getEventId());
        entity.setClientMessageId(event.getClientMessageId());
        entity.setVideoPublicId(event.getVideoPublicId());
        entity.setVideoTimeMs(event.getVideoTimeMs());
        entity.setDisplayTimeMs(event.getDisplayTimeMs());
        entity.setSeq(event.getSeq());
        entity.setAccountId(event.getAccountId());
        var userResult = userFeignClient.getUserByAccountId(event.getAccountId());
        if (userResult == null || userResult.getData() == null || userResult.getData().getUserId() == null) {
            throw new IllegalStateException("账号不存在，等待 Kafka 重试");
        }
        entity.setUserId(userResult.getData().getUserId());
        entity.setUsernameSnapshot(event.getUsernameSnapshot());
        entity.setAvatarSnapshot(event.getAvatarSnapshot());
        entity.setContent(event.getContent());
        entity.setStatus(event.getStatus() == null ? 1 : event.getStatus());
        entity.setVersion(0);
        LocalDateTime now = LocalDateTime.now();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        try {
            int inserted = mapper.insert(entity);
            if (inserted > 0) {
                realtimeService.publishPersisted(event);
            }
        } catch (DuplicateKeyException ignored) {
            // Kafka 至少一次消费，eventId/clientMessageId 唯一键保证幂等；直接插入也避免查后插竞态。
        } catch (RuntimeException e) {
            log.error("弹幕持久化失败，等待 Kafka 重投: id={}, eventId={}", event.getId(), event.getEventId(), e);
            throw e;
        }
    }
}
