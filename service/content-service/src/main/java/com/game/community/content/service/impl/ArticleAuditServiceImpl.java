package com.game.community.content.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.event.AiTaskProducer;
import com.game.community.content.mapper.ArticleAuditMapper;
import com.game.community.content.service.ArticleAuditService;
import com.game.community.model.dto.aiagent.ModerationImageRequest;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.entity.article.ArticleAudit;
import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.message.AiTaskRequestMessage;
import com.game.community.model.message.ArticleModerationContext;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import com.game.community.utils.MinIOUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 内容审核适配层：组装图片载荷并投递 Kafka，审核结果由结果监听器回写。 */
@Service
@RequiredArgsConstructor
public class ArticleAuditServiceImpl implements ArticleAuditService {

    private final AiTaskProducer aiTaskProducer;
    private final ArticleAuditMapper articleAuditMapper;
    private final MinIOUtils minIOUtils;

    @Override
    public void dispatchArticleAudit(ArticleModerationContext context) {
        ModerationRequest request = new ModerationRequest();
        request.setType(ModerationContentType.ARTICLE);
        request.setTitle(context.getArticle().getTitle());
        request.setContent(context.getArticle().getContent());
        List<String> imageUrls = new ArrayList<>();
        if (StringUtils.hasText(context.getCoverUrl())) {
            imageUrls.add(context.getCoverUrl());
        }
        if (context.getImageUrls() != null) {
            imageUrls.addAll(context.getImageUrls());
        }
        request.setImages(toImageRequests(imageUrls));
        aiTaskProducer.send(AiTaskRequestMessage.MODERATION, context.getArticleId(), request, context);
    }

    @Override
    public void recordAudit(Long articleId, ModerationResultVO result, List<String> imageUrls) {
        if (result == null) {
            return;
        }
        boolean keywordPass = result.getKeywordAudit() == ModerationCheckResult.PASS;
        saveAuditRecord(articleId, ContentConstants.AuditStage.LOCAL_TEXT,
                keywordPass ? ContentConstants.AuditStatus.PASS : ContentConstants.AuditStatus.BLOCK,
                keywordPass ? "pass" : "block", keywordPass ? "通过" : result.getReason());
        if (result.getKeywordAudit() == ModerationCheckResult.REJECT) {
            return;
        }
        saveAuditRecord(articleId, ContentConstants.AuditStage.AI_TEXT, auditStatus(result.getResult()),
                suggestion(result.getResult()), result.getReason());
        if (imageUrls != null && imageUrls.stream().anyMatch(StringUtils::hasText)) {
            saveAuditRecord(articleId, ContentConstants.AuditStage.AI_IMAGE, auditStatus(result.getResult()),
                    suggestion(result.getResult()), result.getReason());
        }
    }

    /** 公网图片直接传 URL；私有 pending 对象转换为短时签名 URL；data URL 才传 Base64。 */
    private List<ModerationImageRequest> toImageRequests(List<String> imageUrls) {
        List<ModerationImageRequest> requests = new ArrayList<>();
        if (imageUrls == null) {
            return requests;
        }
        for (String imageUrl : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            ModerationImageRequest request = new ModerationImageRequest();
            if (imageUrl.startsWith("pending://")) {
                request.setImageUrl(minIOUtils.generatePrivateUrl(
                        imageUrl.substring("pending://".length()), 900));
            } else if (imageUrl.startsWith("data:")) {
                request.setImageBase64(imageUrl);
                int separator = imageUrl.indexOf(';');
                if (separator > 5) {
                    request.setMimeType(imageUrl.substring(5, separator));
                }
            } else if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
                request.setImageUrl(imageUrl);
            } else {
                throw new IllegalArgumentException("图片链接格式不支持，转人工审核");
            }
            requests.add(request);
        }
        return requests;
    }

    private int auditStatus(ModerationDecision decision) {
        if (decision == ModerationDecision.PASS) {
            return ContentConstants.AuditStatus.PASS;
        }
        if (decision == ModerationDecision.HUMAN_REVIEW) {
            return ContentConstants.AuditStatus.REVIEW;
        }
        return ContentConstants.AuditStatus.BLOCK;
    }

    private String suggestion(ModerationDecision decision) {
        if (decision == ModerationDecision.PASS) {
            return "pass";
        }
        if (decision == ModerationDecision.HUMAN_REVIEW) {
            return "review";
        }
        return "block";
    }

    private void saveAuditRecord(Long articleId, int stage, int status, String suggestion, String reason) {
        ArticleAudit audit = new ArticleAudit();
        audit.setArticleId(articleId);
        audit.setAuditStage(stage);
        audit.setStatus(status);
        audit.setSuggestion(suggestion);
        audit.setReason(reason);
        audit.setAuditTime(LocalDateTime.now());
        audit.setCreateTime(LocalDateTime.now());
        audit.setUpdateTime(LocalDateTime.now());
        articleAuditMapper.insert(audit);
    }
}
