package com.game.community.content.service;

import com.game.community.model.message.ArticleModerationContext;
import com.game.community.model.vo.aiagent.ModerationResultVO;

import java.util.List;

/**
 * 文章审核服务接口
 */
public interface ArticleAuditService {

    /** 投递文章审核任务；调用方不等待模型结果。 */
    void dispatchArticleAudit(ArticleModerationContext context);

    /** AI 结果返回后记录文章审核流水。 */
    void recordAudit(Long articleId, ModerationResultVO result, List<String> imageUrls);
}
