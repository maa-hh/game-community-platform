package com.game.community.content.service.impl;

import com.game.community.common.constant.content.ContentConstants;
import com.game.community.content.mapper.ArticleAuditMapper;
import com.game.community.content.service.ArticleAuditService;
import com.game.community.feign.AiAgentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.dto.aiagent.ModerationRequest;
import com.game.community.model.dto.aiagent.ModerationImageRequest;
import com.game.community.model.entity.article.ArticleAudit;
import com.game.community.model.enums.aiagent.ModerationCheckResult;
import com.game.community.model.enums.aiagent.ModerationContentType;
import com.game.community.model.enums.aiagent.ModerationDecision;
import com.game.community.model.vo.aiagent.ModerationResultVO;
import com.game.community.utils.MinIOUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * 内容审核服务：
 * 审核规则统一由 ai-agent-service 执行，本服务只保存文章审核流水。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleAuditServiceImpl implements ArticleAuditService {

    private final AiAgentFeignClient aiAgentFeignClient;

    private final ArticleAuditMapper articleAuditMapper;

    private final MinIOUtils minIOUtils;

    @Override
    public ModerationResultVO auditArticle(Long articleId, String title, String content, List<String> imageUrls) {
        ModerationRequest articleRequest = new ModerationRequest();
        articleRequest.setType(ModerationContentType.ARTICLE);
        articleRequest.setTitle(title);
        articleRequest.setContent(content);
        articleRequest.setImages(toImageRequests(imageUrls));
        ModerationResultVO articleResult = moderate(articleRequest);
        boolean keywordPass = articleResult.getKeywordAudit() == ModerationCheckResult.PASS;
        saveAuditRecord(articleId, ContentConstants.AuditStage.LOCAL_TEXT,
                keywordPass ? ContentConstants.AuditStatus.PASS : ContentConstants.AuditStatus.BLOCK,
                keywordPass ? "pass" : "block",
                keywordPass ? "通过" : articleResult.getReason());
        if (articleResult.getKeywordAudit() == ModerationCheckResult.REJECT) {
            return articleResult;
        }

        saveAuditRecord(articleId, ContentConstants.AuditStage.AI_TEXT,
                auditStatus(articleResult.getResult()), suggestion(articleResult.getResult()),
                articleResult.getReason());
        if (imageUrls != null && imageUrls.stream().anyMatch(StringUtils::hasText)) {
            saveAuditRecord(articleId, ContentConstants.AuditStage.AI_IMAGE,
                    auditStatus(articleResult.getResult()), suggestion(articleResult.getResult()),
                    articleResult.getReason());
        }
        return articleResult;
    }

    /** 优先发送图片字节，读取失败时让视觉模型读取 URL；全部图片装入同一个文章请求。 */
    private List<ModerationImageRequest> toImageRequests(List<String> imageUrls) {
        List<ModerationImageRequest> requests = new java.util.ArrayList<>();
        if (imageUrls == null) {
            return requests;
        }
        for (String imageUrl : imageUrls) {
            if (!StringUtils.hasText(imageUrl)) {
                continue;
            }
            ModerationImageRequest request = new ModerationImageRequest();
            request.setImageUrl(imageUrl);
            try {
                MinIOUtils.FilePayload filePayload = minIOUtils.readFileByUrl(imageUrl);
                request.setImageBase64(Base64.getEncoder().encodeToString(filePayload.bytes()));
                request.setMimeType(filePayload.contentType());
            } catch (Exception e) {
                log.warn("文章图片读取失败，回退为 URL 审核: url={}, error={}", imageUrl, e.getMessage());
            }
            requests.add(request);
        }
        return requests;
    }

    /** 调用 AI Agent；服务不可达时转人工审核，避免误放行或误拒绝。 */
    private ModerationResultVO moderate(ModerationRequest request) {
        try {
            Result<ModerationResultVO> response = aiAgentFeignClient.moderate(request);
            if (response != null && response.getCode() != null && response.getCode() == 200
                    && response.getData() != null) {
                return response.getData();
            }
            return unavailable(request.getType(), response == null ? "AI响应为空" : "AI审核返回失败");
        } catch (Exception e) {
            log.warn("AI Agent 审核服务不可用: type={}, error={}", request.getType(), e.getMessage());
            return unavailable(request.getType(), "AI审核服务异常");
        }
    }

    /** 构造跨服务失败时的统一人工审核结果。 */
    private ModerationResultVO unavailable(ModerationContentType type, String error) {
        ModerationResultVO result = new ModerationResultVO();
        result.setType(type);
        result.setKeywordAudit(type == ModerationContentType.TEXT || type == ModerationContentType.ARTICLE
                ? ModerationCheckResult.PASS : ModerationCheckResult.NOT_APPLICABLE);
        result.setMatchedKeywords(List.of());
        result.setAiAudit(ModerationCheckResult.NOT_EXECUTED);
        result.setScore(5);
        result.setReason("AI审核服务不可用，转人工审核" + (StringUtils.hasText(error) ? ": " + error : ""));
        result.setResult(ModerationDecision.HUMAN_REVIEW);
        return result;
    }

    /** 映射文章审核流水状态。 */
    private int auditStatus(ModerationDecision decision) {
        if (decision == ModerationDecision.PASS) {
            return ContentConstants.AuditStatus.PASS;
        }
        if (decision == ModerationDecision.HUMAN_REVIEW) {
            return ContentConstants.AuditStatus.REVIEW;
        }
        return ContentConstants.AuditStatus.BLOCK;
    }

    /** 映射文章审核建议。 */
    private String suggestion(ModerationDecision decision) {
        if (decision == ModerationDecision.PASS) {
            return "pass";
        }
        if (decision == ModerationDecision.HUMAN_REVIEW) {
            return "review";
        }
        return "block";
    }

    /** 保存单个文章审核阶段流水。 */
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
            throw new IllegalStateException("保存文章审核流水失败: articleId=" + articleId + ", stage=" + stage, e);
        }
    }
}
