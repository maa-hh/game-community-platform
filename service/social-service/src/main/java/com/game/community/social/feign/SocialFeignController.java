package com.game.community.social.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.social.service.ReportService;
import com.game.community.social.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/feign/social")
@RequiredArgsConstructor
public class SocialFeignController {

    private final SocialService socialService;

    private final ReportService reportService;

    @GetMapping("/article/stats")
    public Result<List<ArticleStatsVO>> getStats(@RequestParam("articleIds") List<Long> articleIds,
                                                 @RequestParam(value = "userId", required = false) Long userId) {
        return Result.success(socialService.getArticleStatsBatch(userId, articleIds));
    }

    @PostMapping("/feed/publish")
    public Result<Void> publishArticleToFollowers(@RequestParam("authorId") Long authorId,
                                                  @RequestParam("articleId") Long articleId,
                                                  @RequestParam("publishedTime") String publishedTime) {
        socialService.publishArticleToFollowers(authorId, articleId, LocalDateTime.parse(publishedTime));
        return Result.success(null);
    }

    @GetMapping("/comments/{commentId}")
    public Result<CommentVO> getCommentDetail(@PathVariable("commentId") Long commentId) {
        return Result.success(socialService.getCommentDetail(commentId));
    }

    @GetMapping("/replies/{replyId}")
    public Result<ReplyVO> getReplyDetail(@PathVariable("replyId") Long replyId) {
        return Result.success(socialService.getReplyDetail(replyId));
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
}
