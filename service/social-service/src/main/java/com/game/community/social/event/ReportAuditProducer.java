package com.game.community.social.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.social.service.SocialOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ReportAuditProducer {

    private final SocialOutboxService socialOutboxService;

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
        socialOutboxService.enqueue(SocialConstants.EventType.REPORT_AUDIT,
                KafkaTopicConstants.REPORT_AUDIT_TOPIC, String.valueOf(reportId), message);
    }
}
