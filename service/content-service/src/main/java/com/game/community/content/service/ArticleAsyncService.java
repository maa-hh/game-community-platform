package com.game.community.content.service;

import com.game.community.model.dto.article.ArticleDTO;

import java.util.List;

/**
 * 文章审核发布任务执行服务接口。
 */
public interface ArticleAsyncService {

    /**
     * 审核并发布文章
     * 审核通过后：正文存MongoDB，状态改为已发布
     * 审核失败：清理MinIO图片
     *
     * 注意：该方法本身不再开启新的异步线程，异步边界由任务调度层统一负责。
     *
     * @param articleId   文章ID
     * @param userId      用户ID（用于发送WebSocket通知）
     * @param articleDTO  文章DTO
     * @param coverUrl   封面URL
     * @param imageUrls  图片URL列表
     */
    void auditAndPublish(Long articleId, Long userId, ArticleDTO articleDTO,
                         String coverUrl, List<String> imageUrls);
}
