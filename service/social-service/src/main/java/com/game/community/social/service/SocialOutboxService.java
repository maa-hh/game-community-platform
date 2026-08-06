package com.game.community.social.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.model.entity.social.SocialOutboxEvent;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.social.mapper.SocialOutboxMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 可靠事件写入服务。调用方处于业务事务时，事件与业务数据同事务提交。
 */
@Service
@RequiredArgsConstructor
public class SocialOutboxService {

    private final SocialOutboxMapper socialOutboxMapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(String eventType, String topic, String messageKey, Object payload) {
        SocialOutboxEvent event = new SocialOutboxEvent();
        String eventId = payload instanceof ArticleBehaviorMessage behavior
                && behavior.getEventId() != null
                ? behavior.getEventId()
                : UUID.randomUUID().toString();
        event.setEventKey(eventType + ":" + eventId);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setPayload(toJson(payload));
        event.setStatus(0);
        event.setRetryCount(0);
        socialOutboxMapper.insert(event);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("社交 Outbox 消息序列化失败", e);
        }
    }
}
