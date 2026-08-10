package com.game.community.steam.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.SaveGameReviewDTO;
import com.game.community.model.dto.game.GameReviewPageQuery;
import com.game.community.model.vo.game.GameRatingStatsVO;
import com.game.community.model.vo.game.GameReviewVO;

public interface GameReviewService {

    /** 保存当前用户对游戏的评分和短评。 */
    void saveReview(Long appId, SaveGameReviewDTO dto);

    /** 分页查询游戏的有效短评。 */
    PageResult<GameReviewVO> listReviews(GameReviewPageQuery query);

    /** 查询当前用户对游戏的有效短评。 */
    GameReviewVO getMine(Long appId);

    /** 软删除当前用户对游戏的短评。 */
    void deleteMine(Long appId);

    /** 查询游戏评分汇总。 */
    GameRatingStatsVO getRatingStats(Long appId);
}
