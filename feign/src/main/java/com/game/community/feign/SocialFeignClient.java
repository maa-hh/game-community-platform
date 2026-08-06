package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "social-service", contextId = "socialFeignClient", path = "/feign/social")
public interface SocialFeignClient {

    @GetMapping("/article/stats")
    Result<List<ArticleStatsVO>> getStats(@RequestParam("articleIds") List<Long> articleIds,
                                          @RequestParam(value = "userId", required = false) Long userId);

    @PostMapping("/feed/publish")
    Result<Void> publishArticleToFollowers(@RequestParam("authorId") Long authorId,
                                           @RequestParam("articleId") Long articleId,
                                           @RequestParam("publishedTime") String publishedTime,
                                           @RequestHeader("X-Internal-Token") String internalToken);

    @GetMapping("/comments/{commentId}")
    Result<CommentVO> getCommentDetail(@PathVariable("commentId") Long commentId);

    @GetMapping("/replies/{replyId}")
    Result<ReplyVO> getReplyDetail(@PathVariable("replyId") Long replyId);

    @PostMapping("/comments/{commentId}/hide")
    Result<Void> hideCommentByAudit(@PathVariable("commentId") Long commentId);

    @PostMapping("/replies/{replyId}/hide")
    Result<Void> hideReplyByAudit(@PathVariable("replyId") Long replyId);

    @PostMapping("/reports/{reportId}/handle")
    Result<Void> markReportHandled(@PathVariable("reportId") Long reportId,
                                   @RequestParam("status") Integer status,
                                   @RequestParam("handlerId") Long handlerId,
                                   @RequestParam(value = "handleRemark", required = false) String handleRemark);

    @GetMapping("/block/has-relation")
    Result<Boolean> hasBlackRelation(@RequestParam("viewerId") Long viewerId,
                                     @RequestParam("targetUserId") Long targetUserId);
}
