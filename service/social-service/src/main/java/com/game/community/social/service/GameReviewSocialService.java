package com.game.community.social.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.AddGameReviewReplyDTO;
import com.game.community.model.vo.social.GameReviewReplyVO;
import com.game.community.model.vo.social.GameReviewSocialStatsVO;

import java.time.LocalDateTime;
import java.util.List;

public interface GameReviewSocialService {

    void ensureReview(String reviewId, Long appId, Long userId, String content, LocalDateTime createTime);

    void removeReview(String reviewId);

    List<GameReviewSocialStatsVO> listStats(List<String> reviewIds, Long userId);

    PageResult<GameReviewSocialStatsVO> rank(Long appId, Long page, Long size, Long userId);

    PageResult<GameReviewReplyVO> listReplies(Long userId, String reviewId, Long page, Long size);

    String addReply(Long userId, String reviewId, AddGameReviewReplyDTO dto);

    void deleteReply(Long userId, String replyId);

    void likeReview(Long userId, String reviewId);

    void unlikeReview(Long userId, String reviewId);

    void likeReply(Long userId, String replyId);

    void unlikeReply(Long userId, String replyId);
}
