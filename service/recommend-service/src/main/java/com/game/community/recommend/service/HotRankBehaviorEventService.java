package com.game.community.recommend.service;

import com.game.community.model.dto.recommend.ArticleRankScoreAgg;
import com.game.community.model.enums.recommend.HotRankBoardType;
import com.game.community.model.message.ArticleBehaviorMessage;

import java.util.List;

public interface HotRankBehaviorEventService {

    boolean saveEvent(ArticleBehaviorMessage message, double scoreDelta);

    List<ArticleRankScoreAgg> aggregate(HotRankBoardType boardType, String periodKey, Long categoryId);
}
