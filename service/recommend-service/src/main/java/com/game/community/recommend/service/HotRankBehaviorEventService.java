package com.game.community.recommend.service;

import com.game.community.model.dto.recommend.ArticleRankScoreAgg;
import com.game.community.model.enums.recommend.HotRankBoardType;
import com.game.community.model.message.ArticleBehaviorMessage;

import java.time.LocalDateTime;
import java.util.List;

public interface HotRankBehaviorEventService {

    boolean saveEvent(ArticleBehaviorMessage message, double scoreDelta);

    /** 查询单篇文章在时间范围内的精确事件积分，用于幂等重试修复 Redis 投影。 */
    double sumArticleScore(Long articleId, LocalDateTime start, LocalDateTime end);

    List<ArticleRankScoreAgg> aggregate(HotRankBoardType boardType, String periodKey, Long categoryId);
}
