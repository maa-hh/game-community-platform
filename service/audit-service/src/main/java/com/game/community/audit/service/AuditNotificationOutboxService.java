package com.game.community.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.audit.mapper.AuditNotificationOutboxMapper;
import com.game.community.model.entity.audit.AuditNotificationOutbox;
import com.game.community.model.message.NotificationEventMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditNotificationOutboxService {

    private final AuditNotificationOutboxMapper mapper;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public void enqueue(NotificationEventMessage event) {
        AuditNotificationOutbox outbox = new AuditNotificationOutbox();
        outbox.setEventKey(event.getEventId());
        try {
            outbox.setPayload(objectMapper.writeValueAsString(event));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审核通知 Outbox 序列化失败", e);
        }
        outbox.setStatus(0);
        outbox.setRetryCount(0);
        outbox.setCreateTime(LocalDateTime.now());
        outbox.setUpdateTime(LocalDateTime.now());
        mapper.insert(outbox);
    }
}
