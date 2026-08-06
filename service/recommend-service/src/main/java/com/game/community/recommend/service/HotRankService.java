package com.game.community.recommend.service;

import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.model.vo.article.HotArticleVO;

import java.time.LocalDate;
import java.util.List;

public interface HotRankService {

    void applyBehaviorDelta(ArticleBehaviorMessage message);

    void rebuildTotalBoard();

    /** 从行为事件重建今日日榜、本周周榜实时 Redis */
    void rebuildLivePeriodBoards();

    void finalizeWeeklyBoard();

    void finalizeDailyBoard(LocalDate date);

    List<HotArticleVO> listRank(String board,
                                Long categoryId,
                                String periodKey,
                                Long userId,
                                boolean refresh);

    void refreshDailyBoard(Long categoryId);
}
