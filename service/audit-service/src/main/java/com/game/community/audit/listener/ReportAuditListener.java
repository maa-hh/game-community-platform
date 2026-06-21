package com.game.community.audit.listener;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ReportAuditMessage;
import com.game.community.audit.service.AuditReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportAuditListener {

    private final AuditReportService auditReportService;

    @KafkaListener(topics = KafkaTopicConstants.REPORT_AUDIT_TOPIC, groupId = "audit-service-report")
    public void onMessage(ReportAuditMessage message) {
        if (message == null || message.getReportId() == null) {
            return;
        }
        log.info("收到举报审核消息: reportId={}, targetType={}, targetId={}",
                message.getReportId(), message.getTargetType(), message.getTargetId());
        auditReportService.receiveReport(message);
    }
}
