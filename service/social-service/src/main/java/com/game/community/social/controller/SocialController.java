package com.game.community.social.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.CommentPageDTO;
import com.game.community.model.dto.social.ReplyPageDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.BrowseHistoryVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.social.service.SocialService;
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

import java.util.List;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    @LoginCheck
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
    public PageResult<CommentVO> listComments(@PathVariable("articleId") Long articleId,
                                              @RequestParam(value = "page", defaultValue = "1") Long page,
                                              @RequestParam(value = "size", defaultValue = "20") Long size) {
        CommentPageDTO dto = new CommentPageDTO();
        dto.setArticleId(articleId);
        dto.setPage(page);
        dto.setSize(size);
        return socialService.listComments(UserThreadLocal.getUserId(), dto);
    }

    @GetMapping("/comment/{commentId}")
    public Result<CommentVO> getCommentDetail(@PathVariable("commentId") Long commentId) {
        return Result.success(socialService.getCommentDetail(commentId));
    }

    @LoginCheck
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
        return Result.success(socialService.getReplyDetail(replyId));
    }

    @LoginCheck
    @PostMapping("/like/article/{articleId}")
    public Result<Void> likeArticle(@PathVariable("articleId") Long articleId) {
        socialService.likeArticle(UserThreadLocal.getUserId(), articleId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/like/article/{articleId}")
    public Result<Void> unlikeArticle(@PathVariable("articleId") Long articleId) {
        socialService.unlikeArticle(UserThreadLocal.getUserId(), articleId);
        return Result.success(null);
    }

    @LoginCheck
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

    @GetMapping("/like/article/check/{articleId}")
    public Result<Boolean> hasLikedArticle(@PathVariable("articleId") Long articleId) {
        return Result.success(socialService.hasLikedArticle(UserThreadLocal.getUserId(), articleId));
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
    public Result<Article> viewArticle(@PathVariable("articleId") Long articleId) {
        return Result.success(socialService.viewArticle(UserThreadLocal.getUserId(), articleId));
    }

    @GetMapping("/article/count/{articleId}")
    public Result<ArticleStatsVO> getArticleStats(@PathVariable("articleId") Long articleId) {
        return Result.success(socialService.getArticleStats(UserThreadLocal.getUserId(), articleId));
    }

    @GetMapping("/article/counts")
    public Result<List<ArticleStatsVO>> getArticleStatsBatch(@RequestParam("articleIds") List<Long> articleIds) {
        return Result.success(socialService.getArticleStatsBatch(UserThreadLocal.getUserId(), articleIds));
    }

    @LoginCheck
    @GetMapping("/browse/history")
    public PageResult<BrowseHistoryVO> listBrowseHistory(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                         @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listBrowseHistory(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/like/article/list")
    public PageResult<Article> listLikedArticles(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                 @RequestParam(value = "size", defaultValue = "20") Long size) {
        return socialService.listLikedArticles(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/feed")
    public PageResult<Article> listFeed(@RequestParam(value = "before", required = false) String before,
                                        @RequestParam(value = "size", defaultValue = "20") Long size) {
        LocalDateTime beforeTime = before == null || before.isBlank() ? null : LocalDateTime.parse(before);
        return socialService.listFeed(UserThreadLocal.getUserId(), beforeTime, size);
    }

    @PostMapping("/internal/feed/publish")
    public Result<Void> publishArticleToFollowers(@RequestParam("authorId") Long authorId,
                                                  @RequestParam("articleId") Long articleId,
                                                  @RequestParam("publishedTime") String publishedTime) {
        socialService.publishArticleToFollowers(authorId, articleId, LocalDateTime.parse(publishedTime));
        return Result.success(null);
    }
}
