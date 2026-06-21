package com.game.community.social.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ReportAuditMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAuditProducer {

    private final KafkaTemplate<String, ReportAuditMessage> kafkaTemplate;

    public void publish(Long reportId, Integer targetType, Long targetId, Long reporterId,
                        Long reportedUserId, String reason) {
        if (reportId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doPublish(reportId, targetType, targetId, reporterId, reportedUserId, reason);
                }
            });
            return;
        }
        doPublish(reportId, targetType, targetId, reporterId, reportedUserId, reason);
    }

    private void doPublish(Long reportId, Integer targetType, Long targetId, Long reporterId,
                           Long reportedUserId, String reason) {
        ReportAuditMessage message = new ReportAuditMessage(reportId, targetType, targetId,
                reporterId, reportedUserId, reason, LocalDateTime.now());
        kafkaTemplate.send(KafkaTopicConstants.REPORT_AUDIT_TOPIC, String.valueOf(reportId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("举报审核消息发送失败: reportId={}", reportId, ex);
                        return;
                    }
                    log.info("举报审核消息发送成功: reportId={}, targetType={}, targetId={}", reportId, targetType, targetId);
                });
    }
}
