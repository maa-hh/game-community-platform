package com.game.community.steam.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.SaveGameReviewDTO;
import com.game.community.model.vo.game.GameRatingStatsVO;
import com.game.community.model.vo.game.GameReviewVO;

public interface GameReviewService {

    void saveReview(Long appId, Long userId, SaveGameReviewDTO dto);

    PageResult<GameReviewVO> listReviews(Long appId, Long page, Long size);

    GameReviewVO getMine(Long appId, Long userId);

    void deleteMine(Long appId, Long userId);

    GameRatingStatsVO getRatingStats(Long appId);
}
