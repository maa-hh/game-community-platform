package com.game.community.content.service;

import com.game.community.utils.audit.AuditResult;

import java.util.List;

/**
 * 文章审核服务接口
 */
public interface ArticleAuditService {

    /**
     * 同步审核文章文本与图片，逐项记录审核流水。
     */
    AuditResult auditArticle(Long articleId, String title, String content, List<String> imageUrls);
}
