package com.game.community.audit.listener;

import com.game.community.audit.service.ModerationService;
import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ModerationTaskMessage;
import com.game.community.model.message.ReportAuditMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationTaskListener {

    private final ModerationService moderationService;

    @KafkaListener(topics = KafkaTopicConstants.REPORT_AUDIT_TOPIC, groupId = "audit-service-moderation-report")
    public void onReport(ReportAuditMessage message) {
        if (message == null || message.getReportId() == null) {
            return;
        }
        log.info("收到举报审核消息: reportId={}", message.getReportId());
        moderationService.receiveReport(message);
    }

    @KafkaListener(topics = KafkaTopicConstants.MODERATION_TASK_TOPIC, groupId = "audit-service-moderation-task")
    public void onModerationTask(ModerationTaskMessage message) {
        if (message == null || message.getSourceId() == null) {
            return;
        }
        log.info("收到人工审核工单: type={}, sourceId={}", message.getTaskType(), message.getSourceId());
        moderationService.receiveTask(message);
    }
}
