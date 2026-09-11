package com.game.community.content.service;

import com.game.community.model.vo.aiagent.ModerationResultVO;

import java.util.List;

/**
 * 文章审核服务接口
 */
public interface ArticleAuditService {

    /**
     * 同步审核文章文本与图片，逐项记录审核流水。
     */
    ModerationResultVO auditArticle(Long articleId, String title, String content, List<String> imageUrls);
}
