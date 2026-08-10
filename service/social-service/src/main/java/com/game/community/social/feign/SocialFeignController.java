package com.game.community.social.feign;

import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.model.json.ApiJsonViews;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.GameReviewSocialStatsVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.social.service.GameReviewSocialService;
import com.game.community.social.service.FollowService;
import com.game.community.social.service.ReportService;
import com.game.community.social.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/feign/social")
@RequiredArgsConstructor
public class SocialFeignController {

    private final SocialService socialService;

    private final GameReviewSocialService gameReviewSocialService;

    private final ReportService reportService;

    private final FollowService followService;

    @Value("${social.internal-token:}")
    private String internalToken;

    @GetMapping("/article/stats")
    @JsonView(ApiJsonViews.Internal.class)
    public Result<List<ArticleStatsVO>> getStats(@RequestParam("articleIds") List<Long> articleIds,
                                                 @RequestParam(value = "userId", required = false) Long userId) {
        return Result.success(socialService.getArticleStatsBatch(userId, articleIds));
    }

    @PostMapping("/feed/publish")
    public Result<Void> publishArticleToFollowers(@RequestParam("authorId") Long authorId,
                                                  @RequestParam("articleId") Long articleId,
                                                  @RequestParam("publishedTime") String publishedTime,
                                                  @RequestHeader("X-Internal-Token") String requestToken) {
        if (!validInternalToken(requestToken)) {
            throw new IllegalArgumentException("内部调用未授权");
        }
        socialService.publishArticleToFollowers(authorId, articleId, LocalDateTime.parse(publishedTime));
        return Result.success(null);
    }

    private boolean validInternalToken(String requestToken) {
        if (internalToken == null || internalToken.isBlank()
                || requestToken == null || requestToken.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                requestToken.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/comments/{commentId}")
    public Result<CommentVO> getCommentDetail(@PathVariable("commentId") Long commentId) {
        return Result.success(socialService.getCommentDetail(null, commentId));
    }

    @GetMapping("/replies/{replyId}")
    public Result<ReplyVO> getReplyDetail(@PathVariable("replyId") Long replyId) {
        return Result.success(socialService.getReplyDetail(null, replyId));
    }

    @PostMapping("/comments/{commentId}/hide")
    public Result<Void> hideCommentByAudit(@PathVariable("commentId") Long commentId) {
        socialService.hideCommentByAudit(commentId);
        return Result.success(null);
    }

    @PostMapping("/replies/{replyId}/hide")
    public Result<Void> hideReplyByAudit(@PathVariable("replyId") Long replyId) {
        socialService.hideReplyByAudit(replyId);
        return Result.success(null);
    }

    @PostMapping("/reports/{reportId}/handle")
    public Result<Void> markReportHandled(@PathVariable("reportId") Long reportId,
                                          @RequestParam("status") Integer status,
                                          @RequestParam("handlerId") Long handlerId,
                                          @RequestParam(value = "handleRemark", required = false) String handleRemark) {
        reportService.markReportHandled(handlerId, reportId, status, handleRemark);
        return Result.success(null);
    }

    @GetMapping("/block/has-relation")
    public Result<Boolean> hasBlackRelation(@RequestParam("viewerId") Long viewerId,
                                          @RequestParam("targetUserId") Long targetUserId) {
        return Result.success(followService.hasBlackRelation(viewerId, targetUserId));
    }

    @PostMapping("/game-reviews/ensure")
    public Result<Void> ensureGameReview(@RequestParam("reviewId") String reviewId,
                                         @RequestParam("appId") Long appId,
                                         @RequestParam("userId") Long userId,
                                         @RequestParam(value = "content", required = false) String content,
                                         @RequestParam(value = "createTime", required = false) String createTime) {
        gameReviewSocialService.ensureReview(reviewId, appId, userId, content,
                createTime == null || createTime.isBlank() ? null : LocalDateTime.parse(createTime));
        return Result.success(null);
    }

    @PostMapping("/game-reviews/{reviewId}/remove")
    public Result<Void> removeGameReview(@PathVariable("reviewId") String reviewId) {
        gameReviewSocialService.removeReview(reviewId);
        return Result.success(null);
    }

    @GetMapping("/game-reviews/stats")
    public Result<List<GameReviewSocialStatsVO>> getGameReviewStats(
            @RequestParam("reviewIds") List<String> reviewIds,
            @RequestParam(value = "userId", required = false) Long userId) {
        return Result.success(gameReviewSocialService.listStats(reviewIds, userId));
    }

    @GetMapping("/game-reviews/rank")
    public PageResult<GameReviewSocialStatsVO> rankGameReviews(@RequestParam("appId") Long appId,
                                                               @RequestParam("page") Long page,
                                                               @RequestParam("size") Long size,
                                                               @RequestParam(value = "userId", required = false) Long userId) {
        return gameReviewSocialService.rank(appId, page, size, userId);
    }
}
