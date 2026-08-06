package com.game.community.content.service;

import com.alibaba.fastjson2.JSON;
import com.game.community.content.mapper.ContentOutboxMapper;
import com.game.community.model.entity.content.ContentOutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 可靠事件写入服务。调用方若处于业务事务中，Outbox 会与业务数据同事务提交。
 */
@Service
@RequiredArgsConstructor
public class ContentOutboxService {

    private final ContentOutboxMapper contentOutboxMapper;

    public void enqueue(String eventKey, String eventType, String topic, String messageKey, Object payload) {
        ContentOutboxEvent event = new ContentOutboxEvent();
        event.setEventKey(eventKey);
        event.setEventType(eventType);
        event.setTopic(topic);
        event.setMessageKey(messageKey);
        event.setPayload(JSON.toJSONString(payload));
        event.setStatus(0);
        event.setRetryCount(0);
        event.setCreateTime(LocalDateTime.now());
        event.setUpdateTime(LocalDateTime.now());
        contentOutboxMapper.insert(event);
    }
}
