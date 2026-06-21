package com.game.community.content.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.mapper.ArticleAuditMapper;
import com.game.community.content.service.ArticleAuditService;
import com.game.community.model.entity.article.ArticleAudit;
import com.game.community.utils.DfaAuditUtils;
import com.game.community.utils.MinIOUtils;
import com.game.community.utils.audit.AuditClient;
import com.game.community.utils.audit.AuditResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 内容审核服务：
 * 1. 本地 DFA 快速兜底；
 * 2. AI 文本审核；
 * 3. AI 图片 URL 审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleAuditServiceImpl implements ArticleAuditService {

    private final DfaAuditUtils dfaAuditUtils;

    private final AuditClient auditClient;

    private final ArticleAuditMapper articleAuditMapper;

    private final MinIOUtils minIOUtils;

    @Override
    public AuditResult auditArticle(Long articleId, String title, String content, List<String> imageUrls) {
        String fullText = ((title == null ? "" : title) + "\n" + (content == null ? "" : content)).trim();

        boolean dfaPass = dfaAuditUtils.pass(fullText);
        saveAuditRecord(articleId, ContentConstants.AuditStage.LOCAL_TEXT,
                dfaPass ? ContentConstants.AuditStatus.PASS : ContentConstants.AuditStatus.BLOCK,
                dfaPass ? "pass" : "block",
                dfaPass ? "通过" : "命中本地敏感词");
        if (!dfaPass) {
            return AuditResult.reject("命中本地敏感词");
        }

        AuditResult textResult = auditClient.auditText(fullText);
        saveAuditRecord(articleId, ContentConstants.AuditStage.AI_TEXT,
                textResult.isPass() ? ContentConstants.AuditStatus.PASS : ContentConstants.AuditStatus.BLOCK,
                textResult.isPass() ? "pass" : "block",
                textResult.getReason());
        if (!textResult.isPass()) {
            return textResult;
        }

        if (imageUrls != null) {
            for (String imageUrl : imageUrls) {
                if (!StringUtils.hasText(imageUrl)) {
                    continue;
                }
                AuditResult imageResult = auditImage(imageUrl);
                saveAuditRecord(articleId, ContentConstants.AuditStage.AI_IMAGE,
                        imageResult.isPass() ? ContentConstants.AuditStatus.PASS : ContentConstants.AuditStatus.BLOCK,
                        imageResult.isPass() ? "pass" : "block",
                        imageResult.getReason());
                if (!imageResult.isPass()) {
                    return imageResult;
                }
            }
        }

        return AuditResult.pass();
    }

    private AuditResult auditImage(String imageUrl) {
        try {
            MinIOUtils.FilePayload filePayload = minIOUtils.readFileByUrl(imageUrl);
            return auditClient.auditImage(filePayload.bytes(), filePayload.contentType());
        } catch (Exception e) {
            log.warn("文章图片读取失败，回退为 URL 审核: url={}, error={}", imageUrl, e.getMessage());
            return auditClient.auditImageUrl(imageUrl);
        }
    }

    private void saveAuditRecord(Long articleId, int stage, int status, String suggestion, String reason) {
        try {
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
        } catch (Exception e) {
            log.warn("保存文章审核流水失败: articleId={}, stage={}, error={}", articleId, stage, e.getMessage());
        }
    }
}
