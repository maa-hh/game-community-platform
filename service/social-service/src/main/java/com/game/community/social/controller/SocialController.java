package com.game.community.social.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddGameReviewReplyDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.CommentPageDTO;
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
import com.game.community.social.client.SocialRemoteClient;
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

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;
    private final SocialRemoteClient remoteClient;

    private final GameReviewSocialService gameReviewSocialService;

    @LoginCheck
    @SocialRateLimit(action = "comment:create", limit = 10, windowSeconds = 60)
    @PostMapping("/comment")
    public Result<Long> addComment(@Valid @RequestBody AddCommentDTO dto) {
        dto.setInternalArticleId(resolvePublicArticleId(dto.getArticleId()));
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
        dto.setArticleId(resolvePublicArticleId(publicId));
        dto.setPage(page);
        dto.setSize(size);
        return socialService.listComments(UserThreadLocal.getUserId(), dto);
    }

    @GetMapping("/comment/{commentId}")
    public Result<CommentVO> getCommentDetail(@PathVariable("commentId") Long commentId) {
        return Result.success(socialService.getCommentDetail(UserThreadLocal.getUserId(), commentId));
    }

    @LoginCheck
    @SocialRateLimit(action = "reply:create", limit = 20, windowSeconds = 60)
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
    @SocialRateLimit(action = "like:article", limit = 120, windowSeconds = 60)
    @PostMapping("/like/article/{articleId}")
    public Result<Void> likeArticle(@PathVariable("articleId") String publicId) {
        socialService.likeArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId));
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/like/article/{articleId}")
    public Result<Void> unlikeArticle(@PathVariable("articleId") String publicId) {
        socialService.unlikeArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId));
        return Result.success(null);
    }

    @LoginCheck
    @SocialRateLimit(action = "like:comment", limit = 120, windowSeconds = 60)
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
    @SocialRateLimit(action = "like:reply", limit = 120, windowSeconds = 60)
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
        return Result.success(socialService.hasLikedArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId)));
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
        return Result.success(socialService.viewArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId)));
    }

    @GetMapping("/article/count/{articleId}")
    @JsonView(ApiJsonViews.Public.class)
    public Result<ArticleStatsVO> getArticleStats(@PathVariable("articleId") String publicId) {
        ArticleStatsVO stats = socialService.getArticleStats(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId));
        stats.setPublicId(publicId);
        return Result.success(stats);
    }

    @GetMapping("/article/counts")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<ArticleStatsVO>> getArticleStatsBatch(@RequestParam("articleIds") List<String> publicIds) {
        var articles = remoteClient.listArticlesByPublicIds(publicIds).stream()
                .collect(java.util.stream.Collectors.toMap(ArticleListVO::getPublicId,
                        java.util.function.Function.identity(), (left, right) -> left));
        java.util.Map<Long, ArticleListVO> articlesById = articles.values().stream()
                .filter(article -> article.getId() != null)
                .collect(java.util.stream.Collectors.toMap(ArticleListVO::getId,
                        java.util.function.Function.identity(), (left, right) -> left));
        List<Long> articleIds = publicIds.stream().map(publicId -> {
            ArticleListVO article = articles.get(publicId);
            if (article == null || article.getId() == null) {
                throw new BusinessException("帖子不存在");
            }
            return article.getId();
        }).toList();
        List<ArticleStatsVO> stats = socialService.getArticleStatsBatch(UserThreadLocal.getUserId(), articleIds);
        stats.forEach(item -> {
            ArticleListVO article = articlesById.get(item.getArticleId());
            if (article != null) {
                item.setPublicId(article.getPublicId());
            }
        });
        return Result.success(stats);
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
    @SocialRateLimit(action = "favorite:article", limit = 60, windowSeconds = 60)
    @PostMapping("/favorite/article/{articleId}")
    public Result<Void> favoriteArticle(@PathVariable("articleId") String publicId) {
        socialService.favoriteArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId));
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/favorite/article/{articleId}")
    public Result<Void> unfavoriteArticle(@PathVariable("articleId") String publicId) {
        socialService.unfavoriteArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId));
        return Result.success(null);
    }

    @GetMapping("/favorite/article/check/{articleId}")
    public Result<Boolean> hasFavoritedArticle(@PathVariable("articleId") String publicId) {
        return Result.success(socialService.hasFavoritedArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId)));
    }

    @LoginCheck
    @GetMapping("/favorite/article/list")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listFavoritedArticles(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                     @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listFavoritedArticles(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @SocialRateLimit(action = "share:article", limit = 30, windowSeconds = 60)
    @PostMapping("/share/article/{articleId}")
    public Result<Void> shareArticle(@PathVariable("articleId") String publicId,
                                     @RequestBody(required = false) ShareArticleDTO dto) {
        socialService.shareArticle(UserThreadLocal.getUserId(), resolvePublicArticleId(publicId), dto);
        return Result.success(null);
    }

    @LoginCheck
    @GetMapping("/feed")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<ArticleListVO> listFeed(@RequestParam(value = "before", required = false) String before,
                                        @RequestParam(value = "beforeArticleId", required = false) Long beforeArticleId,
                                        @RequestParam(value = "size", defaultValue = "20") Long size,
                                        @RequestParam(value = "postType", required = false) Integer postType,
                                        @RequestParam(value = "includeSelf", defaultValue = "true") Boolean includeSelf) {
        LocalDateTime beforeTime = before == null || before.isBlank() ? null : LocalDateTime.parse(before);
        return socialService.listFeed(UserThreadLocal.getUserId(), beforeTime, beforeArticleId, size, postType, includeSelf);
    }

    private Long resolvePublicArticleId(String publicId) {
        ArticleListVO article = remoteClient.getArticleByPublicId(publicId);
        if (article == null || article.getId() == null) {
            throw new BusinessException("帖子不存在");
        }
        return article.getId();
    }
}
