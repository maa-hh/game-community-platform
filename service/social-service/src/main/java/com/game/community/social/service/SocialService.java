package com.game.community.social.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.CommentPageDTO;
import com.game.community.model.dto.social.ReplyPageDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.BrowseHistoryVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;

import java.util.List;
import java.time.LocalDateTime;

public interface SocialService {

    Long addComment(Long userId, AddCommentDTO dto);

    void deleteComment(Long userId, Long commentId);

    PageResult<CommentVO> listComments(Long userId, CommentPageDTO dto);

    Long addReply(Long userId, AddReplyDTO dto);

    void deleteReply(Long userId, Long replyId);

    CommentVO getCommentDetail(Long commentId);

    ReplyVO getReplyDetail(Long replyId);

    void hideCommentByAudit(Long commentId);

    void hideReplyByAudit(Long replyId);

    PageResult<ReplyVO> listReplies(Long userId, ReplyPageDTO dto);

    void likeArticle(Long userId, Long articleId);

    void unlikeArticle(Long userId, Long articleId);

    void likeComment(Long userId, Long commentId);

    void unlikeComment(Long userId, Long commentId);

    void likeReply(Long userId, Long replyId);

    void unlikeReply(Long userId, Long replyId);

    boolean hasLikedArticle(Long userId, Long articleId);

    boolean hasLikedComment(Long userId, Long commentId);

    boolean hasLikedReply(Long userId, Long replyId);

    Article viewArticle(Long userId, Long articleId);

    ArticleStatsVO getArticleStats(Long userId, Long articleId);

    List<ArticleStatsVO> getArticleStatsBatch(Long userId, List<Long> articleIds);

    PageResult<BrowseHistoryVO> listBrowseHistory(Long userId, Long page, Long size);

    PageResult<Article> listLikedArticles(Long userId, Long page, Long size);

    PageResult<Article> listFeed(Long userId, LocalDateTime before, Long size);

    void publishArticleToFollowers(Long authorId, Long articleId, LocalDateTime publishedTime);
}
