package com.game.community.content.event;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.audit.ModerationConstants;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.content.service.ContentOutboxService;
import com.game.community.model.message.ModerationTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModerationTaskProducer {

    private final ContentOutboxService contentOutboxService;

    public void publishArticleAudit(Long articleId, Long userId, String title, String content, String reason,
                                    LocalDateTime targetUpdatedAt) {
        if (articleId == null) {
            return;
        }
        ModerationTaskMessage message = new ModerationTaskMessage();
        message.setTaskType(ModerationConstants.TaskType.ARTICLE_AUDIT);
        message.setSourceId(articleId);
        message.setTargetType(SocialConstants.ReportTargetType.ARTICLE);
        message.setTargetId(articleId);
        message.setSubjectUserId(userId);
        message.setReason(StringUtils.hasText(reason) ? reason : "帖子需人工审核");
        message.setSummary(buildSummary(title, content));
        message.setTargetStatusSnapshot(ModerationConstants.TargetStatusSnapshot.ARTICLE_PENDING);
        message.setTargetUpdatedAt(targetUpdatedAt);
        message.setEventTime(LocalDateTime.now());
        contentOutboxService.enqueue(
                "moderation:article-audit:" + articleId + ":" + UUID.randomUUID(),
                "MODERATION_TASK", KafkaTopicConstants.MODERATION_TASK_TOPIC,
                String.valueOf(articleId), message);
    }

    private String buildSummary(String title, String content) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(title)) {
            builder.append(title.trim());
        }
        if (StringUtils.hasText(content)) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(content.trim());
        }
        String text = builder.toString();
        if (text.length() <= 512) {
            return text;
        }
        return text.substring(0, 512);
    }
}
