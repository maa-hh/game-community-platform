package com.game.community.social.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.CommentPageDTO;
import com.game.community.model.dto.social.FeedQueryDTO;
import com.game.community.model.dto.social.ReplyPageDTO;
import com.game.community.model.dto.social.ShareArticleDTO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.BrowseHistoryVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.MyCommentFeedVO;
import com.game.community.model.vo.social.ReplyVO;

import java.util.List;
import java.time.LocalDateTime;

public interface SocialService {

    Long addComment(Long userId, AddCommentDTO dto);

    void deleteComment(Long userId, Long commentId);

    PageResult<CommentVO> listComments(Long userId, CommentPageDTO dto);

    Long addReply(Long userId, AddReplyDTO dto);

    void deleteReply(Long userId, Long replyId);

    CommentVO getCommentDetail(Long userId, Long commentId);

    ReplyVO getReplyDetail(Long userId, Long replyId);

    void hideCommentByAudit(Long commentId);

    void hideReplyByAudit(Long replyId);

    PageResult<ReplyVO> listReplies(Long userId, ReplyPageDTO dto);

    void likeArticle(Long userId, Long articleId);

    void likeArticle(Long userId, String articlePublicId);

    void unlikeArticle(Long userId, Long articleId);

    void unlikeArticle(Long userId, String articlePublicId);

    void likeComment(Long userId, Long commentId);

    void unlikeComment(Long userId, Long commentId);

    void likeReply(Long userId, Long replyId);

    void unlikeReply(Long userId, Long replyId);

    boolean hasLikedArticle(Long userId, Long articleId);

    boolean hasLikedArticle(Long userId, String articlePublicId);

    boolean hasLikedComment(Long userId, Long commentId);

    boolean hasLikedReply(Long userId, Long replyId);

    ArticleListVO viewArticle(Long userId, Long articleId);

    ArticleListVO viewArticle(Long userId, String articlePublicId);

    ArticleStatsVO getArticleStats(Long userId, Long articleId);

    ArticleStatsVO getArticleStats(Long userId, String articlePublicId);

    List<ArticleStatsVO> getArticleStatsBatch(Long userId, List<Long> articleIds);

    List<ArticleStatsVO> getArticleStatsBatchByPublicIds(Long userId, List<String> articlePublicIds);

    PageResult<BrowseHistoryVO> listBrowseHistory(Long userId, Long page, Long size);

    PageResult<ArticleListVO> listLikedArticles(Long userId, Long page, Long size);

    PageResult<ArticleListVO> listFeed(Long userId, FeedQueryDTO query);

    PageResult<MyCommentFeedVO> listMyComments(Long userId, Long page, Long size);

    PageResult<MyCommentFeedVO> listMyReplies(Long userId, Long page, Long size);

    PageResult<MyCommentFeedVO> listMyLikedComments(Long userId, Long page, Long size);

    PageResult<MyCommentFeedVO> listMyLikedReplies(Long userId, Long page, Long size);

    PageResult<MyCommentFeedVO> listReceivedComments(Long userId, Long page, Long size);

    PageResult<MyCommentFeedVO> listReceivedReplies(Long userId, Long page, Long size);

    PageResult<ArticleListVO> listReceivedLikedArticles(Long userId, Long page, Long size);

    PageResult<MyCommentFeedVO> listReceivedArticleLikes(Long userId, Long page, Long size);

    Long countReceivedLikes(Long userId);

    void publishArticleToFollowers(Long authorId, Long articleId, LocalDateTime publishedTime);

    void removeArticleFromFeeds(Long articleId);

    void favoriteArticle(Long userId, Long articleId);

    void favoriteArticle(Long userId, String articlePublicId);

    void unfavoriteArticle(Long userId, Long articleId);

    void unfavoriteArticle(Long userId, String articlePublicId);

    boolean hasFavoritedArticle(Long userId, Long articleId);

    boolean hasFavoritedArticle(Long userId, String articlePublicId);

    PageResult<ArticleListVO> listFavoritedArticles(Long userId, Long page, Long size);

    void shareArticle(Long userId, Long articleId, ShareArticleDTO dto);

    void shareArticle(Long userId, String articlePublicId, ShareArticleDTO dto);
}
