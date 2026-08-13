package com.game.community.social.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.social.SocialRateLimitConstants;
import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddGameReviewReplyDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.ArticleStatsBatchQueryDTO;
import com.game.community.model.dto.social.CommentPageDTO;
import com.game.community.model.dto.social.FeedQueryDTO;
import com.game.community.model.dto.social.ReplyPageDTO;
import com.game.community.model.dto.social.ShareArticleDTO;
import com.game.community.model.json.ApiJsonViews;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.BrowseHistoryVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.GameReviewReplyVO;
import com.game.community.model.vo.social.MyCommentFeedVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.social.service.GameReviewSocialService;
import com.game.community.social.service.SocialService;
import com.game.community.social.aspect.SocialRateLimit;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;
    private final GameReviewSocialService gameReviewSocialService;

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.COMMENT_CREATE,
            limit = SocialRateLimitConstants.COMMENT_CREATE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/comment")
    public Result<Long> addComment(@Valid @RequestBody AddCommentDTO dto) {
        return Result.success(socialService.addComment(UserThreadLocal.getUserId(), dto));
    }

    @LoginCheck
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable("commentId") Long commentId) {
        socialService.deleteComment(UserThreadLocal.getUserId(), commentId);
        return Result.success(null);
    }

    @GetMapping("/comment/list/{articleId}")
    public PageResult<CommentVO> listComments(@PathVariable("articleId") String publicId,
                                              @RequestParam(value = "page", defaultValue = "1") Long page,
                                              @RequestParam(value = "size", defaultValue = "20") Long size) {
        CommentPageDTO dto = new CommentPageDTO();
        dto.setArticleId(publicId);
        dto.setPage(page);
        dto.setSize(size);
        return socialService.listComments(UserThreadLocal.getUserId(), dto);
    }

    @GetMapping("/comment/{commentId}")
    public Result<CommentVO> getCommentDetail(@PathVariable("commentId") Long commentId) {
        return Result.success(socialService.getCommentDetail(UserThreadLocal.getUserId(), commentId));
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.REPLY_CREATE,
            limit = SocialRateLimitConstants.REPLY_CREATE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/reply")
    public Result<Long> addReply(@Valid @RequestBody AddReplyDTO dto) {
        return Result.success(socialService.addReply(UserThreadLocal.getUserId(), dto));
    }

    @LoginCheck
    @DeleteMapping("/reply/{replyId}")
    public Result<Void> deleteReply(@PathVariable("replyId") Long replyId) {
        socialService.deleteReply(UserThreadLocal.getUserId(), replyId);
        return Result.success(null);
    }

    @GetMapping("/reply/list/{commentId}")
    public PageResult<ReplyVO> listReplies(@PathVariable("commentId") Long commentId,
                                           @RequestParam(value = "page", defaultValue = "1") Long page,
                                           @RequestParam(value = "size", defaultValue = "20") Long size) {
        ReplyPageDTO dto = new ReplyPageDTO();
        dto.setCommentId(commentId);
        dto.setPage(page);
        dto.setSize(size);
        return socialService.listReplies(UserThreadLocal.getUserId(), dto);
    }

    @GetMapping("/reply/{replyId}")
    public Result<ReplyVO> getReplyDetail(@PathVariable("replyId") Long replyId) {
        return Result.success(socialService.getReplyDetail(UserThreadLocal.getUserId(), replyId));
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.LIKE_ARTICLE,
            limit = SocialRateLimitConstants.LIKE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/like/article/{articleId}")
    public Result<Void> likeArticle(@PathVariable("articleId") String publicId) {
        socialService.likeArticle(UserThreadLocal.getUserId(), publicId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/like/article/{articleId}")
    public Result<Void> unlikeArticle(@PathVariable("articleId") String publicId) {
        socialService.unlikeArticle(UserThreadLocal.getUserId(), publicId);
        return Result.success(null);
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.LIKE_COMMENT,
            limit = SocialRateLimitConstants.LIKE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/like/comment/{commentId}")
    public Result<Void> likeComment(@PathVariable("commentId") Long commentId) {
        socialService.likeComment(UserThreadLocal.getUserId(), commentId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/like/comment/{commentId}")
    public Result<Void> unlikeComment(@PathVariable("commentId") Long commentId) {
        socialService.unlikeComment(UserThreadLocal.getUserId(), commentId);
        return Result.success(null);
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.LIKE_REPLY,
            limit = SocialRateLimitConstants.LIKE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/like/reply/{replyId}")
    public Result<Void> likeReply(@PathVariable("replyId") Long replyId) {
        socialService.likeReply(UserThreadLocal.getUserId(), replyId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/like/reply/{replyId}")
    public Result<Void> unlikeReply(@PathVariable("replyId") Long replyId) {
        socialService.unlikeReply(UserThreadLocal.getUserId(), replyId);
        return Result.success(null);
    }

    @GetMapping("/game-reviews/{reviewId}/replies")
    public PageResult<GameReviewReplyVO> listGameReviewReplies(@PathVariable("reviewId") String reviewId,
                                                               @RequestParam(value = "page", defaultValue = "1") Long page,
                                                               @RequestParam(value = "size", defaultValue = "20") Long size) {
        return gameReviewSocialService.listReplies(UserThreadLocal.getUserId(), reviewId, page, size);
    }

    @LoginCheck
    @PostMapping("/game-reviews/{reviewId}/replies")
    public Result<String> addGameReviewReply(@PathVariable("reviewId") String reviewId,
                                             @Valid @RequestBody AddGameReviewReplyDTO dto) {
        return Result.success(gameReviewSocialService.addReply(UserThreadLocal.getUserId(), reviewId, dto));
    }

    @LoginCheck
    @DeleteMapping("/game-reviews/replies/{replyId}")
    public Result<Void> deleteGameReviewReply(@PathVariable("replyId") String replyId) {
        gameReviewSocialService.deleteReply(UserThreadLocal.getUserId(), replyId);
        return Result.success(null);
    }

    @LoginCheck
    @PostMapping("/game-reviews/{reviewId}/like")
    public Result<Void> likeGameReview(@PathVariable("reviewId") String reviewId) {
        gameReviewSocialService.likeReview(UserThreadLocal.getUserId(), reviewId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/game-reviews/{reviewId}/like")
    public Result<Void> unlikeGameReview(@PathVariable("reviewId") String reviewId) {
        gameReviewSocialService.unlikeReview(UserThreadLocal.getUserId(), reviewId);
        return Result.success(null);
    }

    @LoginCheck
    @PostMapping("/game-reviews/replies/{replyId}/like")
    public Result<Void> likeGameReviewReply(@PathVariable("replyId") String replyId) {
        gameReviewSocialService.likeReply(UserThreadLocal.getUserId(), replyId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/game-reviews/replies/{replyId}/like")
    public Result<Void> unlikeGameReviewReply(@PathVariable("replyId") String replyId) {
        gameReviewSocialService.unlikeReply(UserThreadLocal.getUserId(), replyId);
        return Result.success(null);
    }

    @GetMapping("/like/article/check/{articleId}")
    public Result<Boolean> hasLikedArticle(@PathVariable("articleId") String publicId) {
        return Result.success(socialService.hasLikedArticle(UserThreadLocal.getUserId(), publicId));
    }

    @GetMapping("/like/comment/check/{commentId}")
    public Result<Boolean> hasLikedComment(@PathVariable("commentId") Long commentId) {
        return Result.success(socialService.hasLikedComment(UserThreadLocal.getUserId(), commentId));
    }

    @GetMapping("/like/reply/check/{replyId}")
    public Result<Boolean> hasLikedReply(@PathVariable("replyId") Long replyId) {
        return Result.success(socialService.hasLikedReply(UserThreadLocal.getUserId(), replyId));
    }

    @LoginCheck
    @GetMapping("/article/{articleId}")
    @JsonView(ApiJsonViews.Public.class)
    public Result<ArticleListVO> viewArticle(@PathVariable("articleId") String publicId) {
        return Result.success(socialService.viewArticle(UserThreadLocal.getUserId(), publicId));
    }

    @GetMapping("/article/count/{articleId}")
    @JsonView(ApiJsonViews.Public.class)
    public Result<ArticleStatsVO> getArticleStats(@PathVariable("articleId") String publicId) {
        return Result.success(socialService.getArticleStats(UserThreadLocal.getUserId(), publicId));
    }

    @GetMapping("/article/counts")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleStatsVO>> getArticleStatsBatch(
            @Valid @ModelAttribute ArticleStatsBatchQueryDTO query) {
        return Result.success(socialService.getArticleStatsBatchByPublicIds(
                UserThreadLocal.getUserId(), query.getArticleIds()));
    }

    @LoginCheck
    @GetMapping("/comment/my/list")
    public PageResult<MyCommentFeedVO> listMyComments(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                      @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listMyComments(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/reply/my/list")
    public PageResult<MyCommentFeedVO> listMyReplies(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                    @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listMyReplies(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/comment/my/list")
    public PageResult<MyCommentFeedVO> listMyLikedComments(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                           @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listMyLikedComments(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/reply/my/list")
    public PageResult<MyCommentFeedVO> listMyLikedReplies(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                          @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listMyLikedReplies(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/comment/received/list")
    public PageResult<MyCommentFeedVO> listReceivedComments(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                            @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listReceivedComments(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/reply/received/list")
    public PageResult<MyCommentFeedVO> listReceivedReplies(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                           @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listReceivedReplies(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/browse/history")
    public PageResult<BrowseHistoryVO> listBrowseHistory(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                         @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listBrowseHistory(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/article/list")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listLikedArticles(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                 @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listLikedArticles(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/received/list")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listReceivedLikedArticles(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                               @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listReceivedLikedArticles(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/article/received/list")
    public PageResult<MyCommentFeedVO> listReceivedArticleLikes(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                                @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listReceivedArticleLikes(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/received/count")
    public Result<Long> countReceivedLikes() {
        return Result.success(socialService.countReceivedLikes(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.FAVORITE_ARTICLE,
            limit = SocialRateLimitConstants.FAVORITE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/favorite/article/{articleId}")
    public Result<Void> favoriteArticle(@PathVariable("articleId") String publicId) {
        socialService.favoriteArticle(UserThreadLocal.getUserId(), publicId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/favorite/article/{articleId}")
    public Result<Void> unfavoriteArticle(@PathVariable("articleId") String publicId) {
        socialService.unfavoriteArticle(UserThreadLocal.getUserId(), publicId);
        return Result.success(null);
    }

    @GetMapping("/favorite/article/check/{articleId}")
    public Result<Boolean> hasFavoritedArticle(@PathVariable("articleId") String publicId) {
        return Result.success(socialService.hasFavoritedArticle(UserThreadLocal.getUserId(), publicId));
    }

    @LoginCheck
    @GetMapping("/favorite/article/list")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listFavoritedArticles(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                     @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listFavoritedArticles(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.SHARE_ARTICLE,
            limit = SocialRateLimitConstants.SHARE_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/share/article/{articleId}")
    public Result<Void> shareArticle(@PathVariable("articleId") String publicId,
                                     @RequestBody(required = false) ShareArticleDTO dto) {
        socialService.shareArticle(UserThreadLocal.getUserId(), publicId, dto);
        return Result.success(null);
    }

    @LoginCheck
    @GetMapping("/feed")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listFeed(@Valid @ModelAttribute FeedQueryDTO query) {
        return socialService.listFeed(UserThreadLocal.getUserId(), query);
    }
}
