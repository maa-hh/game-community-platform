package com.game.community.user.event.kafka;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.audit.ModerationConstants;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.model.message.ModerationTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationTaskProducer {

    private final KafkaTemplate<String, ModerationTaskMessage> kafkaTemplate;

    public void publishProfileAudit(Long taskId, Long userId, String fieldLabel, String content, String reason,
                                    LocalDateTime targetUpdatedAt) {
        if (taskId == null) {
            return;
        }
        ModerationTaskMessage message = new ModerationTaskMessage();
        message.setTaskType(ModerationConstants.TaskType.PROFILE_AUDIT);
        message.setSourceId(taskId);
        message.setTargetType(SocialConstants.ReportTargetType.USER);
        message.setTargetId(userId);
        message.setSubjectUserId(userId);
        message.setReason(StringUtils.hasText(reason) ? reason : "资料需人工复核");
        message.setExtraPayload(fieldLabel);
        message.setSummary(StringUtils.hasText(content) ? content : fieldLabel);
        message.setTargetStatusSnapshot(ModerationConstants.TargetStatusSnapshot.PROFILE_HUMAN_REVIEW);
        message.setTargetUpdatedAt(targetUpdatedAt);
        message.setEventTime(LocalDateTime.now());
        kafkaTemplate.send(KafkaTopicConstants.MODERATION_TASK_TOPIC, String.valueOf(taskId), message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("资料人工审核工单发送失败: taskId={}", taskId, ex);
                    }
                });
    }
}
