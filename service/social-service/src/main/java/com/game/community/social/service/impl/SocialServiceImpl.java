package com.game.community.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.CommentPageDTO;
import com.game.community.model.dto.social.ReplyPageDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.social.SocialArticleLike;
import com.game.community.model.entity.social.SocialArticleStats;
import com.game.community.model.entity.social.SocialBrowseHistory;
import com.game.community.model.entity.social.SocialComment;
import com.game.community.model.entity.social.SocialCommentLike;
import com.game.community.model.entity.social.SocialFeedItem;
import com.game.community.model.entity.social.SocialFollow;
import com.game.community.model.entity.social.SocialReply;
import com.game.community.model.entity.social.SocialReplyLike;
import com.game.community.model.mongo.SocialCommentContent;
import com.game.community.model.vo.social.ArticleStatsVO;
import com.game.community.model.vo.social.BrowseHistoryVO;
import com.game.community.model.vo.social.CommentVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.social.client.SocialRemoteClient;
import com.game.community.social.mapper.SocialArticleLikeMapper;
import com.game.community.social.mapper.SocialArticleStatsMapper;
import com.game.community.social.mapper.SocialBlackMapper;
import com.game.community.social.mapper.SocialBrowseHistoryMapper;
import com.game.community.social.mapper.SocialCommentLikeMapper;
import com.game.community.social.mapper.SocialCommentMapper;
import com.game.community.social.mapper.SocialFeedItemMapper;
import com.game.community.social.mapper.SocialFollowMapper;
import com.game.community.social.mapper.SocialReplyLikeMapper;
import com.game.community.social.mapper.SocialReplyMapper;
import com.game.community.social.mongo.SocialCommentContentRepository;
import com.game.community.social.event.ArticleBehaviorProducer;
import com.game.community.social.event.NotificationEventProducer;
import com.game.community.social.service.SocialService;
import com.game.community.utils.DfaAuditUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SocialServiceImpl implements SocialService {

    private final SocialCommentMapper commentMapper;
    private final SocialReplyMapper replyMapper;
    private final SocialArticleStatsMapper articleStatsMapper;
    private final SocialBlackMapper blackMapper;
    private final SocialArticleLikeMapper articleLikeMapper;
    private final SocialCommentLikeMapper commentLikeMapper;
    private final SocialReplyLikeMapper replyLikeMapper;
    private final SocialBrowseHistoryMapper browseHistoryMapper;
    private final SocialFeedItemMapper feedItemMapper;
    private final SocialFollowMapper followMapper;
    private final SocialCommentContentRepository commentContentRepository;
    private final SocialRemoteClient remoteClient;
    private final DfaAuditUtils dfaAuditUtils;
    private final ArticleBehaviorProducer articleBehaviorProducer;
    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long userId, AddCommentDTO dto) {
        Article article = requireArticle(dto.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        if (!dfaAuditUtils.pass(dto.getContent())) {
            throw new BusinessException("评论包含敏感内容");
        }
        UserVO user = currentUser(userId);
        SocialComment comment = new SocialComment();
        comment.setArticleId(article.getId());
        comment.setUserId(userId);
        comment.setUsername(user == null ? "玩家" + userId : user.getUsername());
        comment.setAvatar(user == null ? null : user.getAvatar());
        comment.setLikeCount(0L);
        comment.setReplyCount(0L);
        comment.setStatus(SocialConstants.CommentStatus.NORMAL);
        comment.setVersion(0);
        commentMapper.insert(comment);

        SocialCommentContent content = new SocialCommentContent();
        content.setCommentId(comment.getId());
        content.setArticleId(article.getId());
        content.setUserId(userId);
        content.setContent(dto.getContent().trim());
        content.setCreateTime(LocalDateTime.now());
        content.setUpdateTime(LocalDateTime.now());
        commentContentRepository.save(content);
        incrementArticleStats(article.getId(), "comment_count", 1);
        articleBehaviorProducer.publish(article.getId(), 0L, 1L, 0L);
        if (!Objects.equals(userId, article.getUserId())) {
            notificationEventProducer.publishArticleComment(article.getUserId(), user, article.getId(), comment.getId(), content.getContent());
        }
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        SocialComment comment = requireComment(commentId);
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException("无权删除该评论");
        }
        int updated = commentMapper.update(null, new LambdaUpdateWrapper<SocialComment>()
                .eq(SocialComment::getId, commentId)
                .eq(SocialComment::getUserId, userId)
                .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                .set(SocialComment::getStatus, SocialConstants.CommentStatus.DELETED)
                .set(SocialComment::getDeleted, 1));
        if (updated > 0) {
            commentContentRepository.deleteByCommentId(commentId);
            incrementArticleStats(comment.getArticleId(), "comment_count", -1);
            articleBehaviorProducer.publish(comment.getArticleId(), 0L, -1L, 0L);
        }
    }

    @Override
    public PageResult<CommentVO> listComments(Long userId, CommentPageDTO dto) {
        long page = normalizePage(dto.getPage());
        long size = normalizeSize(dto.getSize());
        Page<SocialComment> result = commentMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<SocialComment>()
                .eq(SocialComment::getArticleId, dto.getArticleId())
                .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                .orderByDesc(SocialComment::getCreateTime));
        List<Long> commentIds = result.getRecords().stream().map(SocialComment::getId).toList();
        Map<Long, String> contentMap = commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Set<Long> likedIds = likedCommentIds(userId, commentIds);
        List<CommentVO> records = result.getRecords().stream()
                .map(item -> toCommentVO(item, contentMap.get(item.getId()), likedIds.contains(item.getId())))
                .toList();
        return PageResult.of(records, page, size, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addReply(Long userId, AddReplyDTO dto) {
        SocialComment comment = requireComment(dto.getCommentId());
        Article article = requireArticle(comment.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        assertUserInteractionAllowed(userId, comment.getUserId());
        if (!dfaAuditUtils.pass(dto.getContent())) {
            throw new BusinessException("回复包含敏感内容");
        }
        if (dto.getReplyToUserId() != null) {
            assertUserInteractionAllowed(userId, dto.getReplyToUserId());
        }
        UserVO user = currentUser(userId);
        UserVO replyToUser = dto.getReplyToUserId() == null ? null : userMap(List.of(dto.getReplyToUserId())).get(dto.getReplyToUserId());
        SocialReply reply = new SocialReply();
        reply.setCommentId(comment.getId());
        reply.setArticleId(comment.getArticleId());
        reply.setUserId(userId);
        reply.setUsername(user == null ? "玩家" + userId : user.getUsername());
        reply.setAvatar(user == null ? null : user.getAvatar());
        reply.setReplyToUserId(dto.getReplyToUserId());
        reply.setReplyToUsername(replyToUser == null ? null : replyToUser.getUsername());
        reply.setContent(dto.getContent().trim());
        reply.setLikeCount(0L);
        reply.setStatus(SocialConstants.ReplyStatus.NORMAL);
        reply.setVersion(0);
        replyMapper.insert(reply);
        incrementComment(comment.getId(), "reply_count", 1);
        incrementArticleStats(comment.getArticleId(), "reply_count", 1);
        articleBehaviorProducer.publish(comment.getArticleId(), 0L, 0L, 0L);
        Set<Long> recipients = new LinkedHashSet<>();
        if (!Objects.equals(userId, article.getUserId())) {
            recipients.add(article.getUserId());
        }
        if (dto.getReplyToUserId() != null && !Objects.equals(userId, dto.getReplyToUserId())) {
            recipients.add(dto.getReplyToUserId());
        } else if (!Objects.equals(userId, comment.getUserId())) {
            recipients.add(comment.getUserId());
        }
        for (Long recipientId : recipients) {
            notificationEventProducer.publishCommentReply(recipientId, user, comment.getArticleId(), comment.getId(), reply.getId(), reply.getContent());
        }
        return reply.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long userId, Long replyId) {
        SocialReply reply = requireReply(replyId);
        if (!Objects.equals(reply.getUserId(), userId)) {
            throw new BusinessException("无权删除该回复");
        }
        int updated = replyMapper.update(null, new LambdaUpdateWrapper<SocialReply>()
                .eq(SocialReply::getId, replyId)
                .eq(SocialReply::getUserId, userId)
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                .set(SocialReply::getStatus, SocialConstants.ReplyStatus.DELETED)
                .set(SocialReply::getDeleted, 1));
        if (updated > 0) {
            incrementComment(reply.getCommentId(), "reply_count", -1);
            incrementArticleStats(reply.getArticleId(), "reply_count", -1);
            articleBehaviorProducer.publish(reply.getArticleId(), 0L, 0L, 0L);
        }
    }

    @Override
    public CommentVO getCommentDetail(Long commentId) {
        SocialComment comment = requireComment(commentId);
        String content = commentContentRepository.findByCommentId(commentId)
                .map(SocialCommentContent::getContent)
                .orElse("");
        return toCommentVO(comment, content, false);
    }

    @Override
    public ReplyVO getReplyDetail(Long replyId) {
        return toReplyVO(requireReply(replyId), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideCommentByAudit(Long commentId) {
        SocialComment comment = requireComment(commentId);
        int updated = commentMapper.update(null, new LambdaUpdateWrapper<SocialComment>()
                .eq(SocialComment::getId, commentId)
                .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                .set(SocialComment::getStatus, SocialConstants.CommentStatus.HIDDEN)
                .set(SocialComment::getUpdateTime, LocalDateTime.now()));
        if (updated > 0) {
            incrementArticleStats(comment.getArticleId(), "comment_count", -1);
            articleBehaviorProducer.publish(comment.getArticleId(), 0L, -1L, 0L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideReplyByAudit(Long replyId) {
        SocialReply reply = requireReply(replyId);
        int updated = replyMapper.update(null, new LambdaUpdateWrapper<SocialReply>()
                .eq(SocialReply::getId, replyId)
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                .set(SocialReply::getStatus, SocialConstants.ReplyStatus.HIDDEN)
                .set(SocialReply::getUpdateTime, LocalDateTime.now()));
        if (updated > 0) {
            incrementComment(reply.getCommentId(), "reply_count", -1);
            incrementArticleStats(reply.getArticleId(), "reply_count", -1);
            articleBehaviorProducer.publish(reply.getArticleId(), 0L, 0L, 0L);
        }
    }

    @Override
    public PageResult<ReplyVO> listReplies(Long userId, ReplyPageDTO dto) {
        long page = normalizePage(dto.getPage());
        long size = normalizeSize(dto.getSize());
        Page<SocialReply> result = replyMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<SocialReply>()
                .eq(SocialReply::getCommentId, dto.getCommentId())
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                .orderByAsc(SocialReply::getCreateTime));
        List<Long> replyIds = result.getRecords().stream().map(SocialReply::getId).toList();
        Set<Long> likedIds = likedReplyIds(userId, replyIds);
        List<ReplyVO> records = result.getRecords().stream()
                .map(item -> toReplyVO(item, likedIds.contains(item.getId())))
                .toList();
        return PageResult.of(records, page, size, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long userId, Long articleId) {
        Article article = requireArticle(articleId);
        assertArticleInteractionAllowed(userId, article);
        SocialArticleLike like = new SocialArticleLike();
        like.setArticleId(articleId);
        like.setUserId(userId);
        if (insertIgnoreDuplicate(() -> articleLikeMapper.insert(like))) {
            incrementArticleStats(articleId, "like_count", 1);
            articleBehaviorProducer.publish(articleId, 1L, 0L, 0L);
            if (!Objects.equals(userId, article.getUserId())) {
                notificationEventProducer.publishArticleLike(article.getUserId(), currentUser(userId), articleId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeArticle(Long userId, Long articleId) {
        int deleted = articleLikeMapper.delete(new LambdaQueryWrapper<SocialArticleLike>()
                .eq(SocialArticleLike::getArticleId, articleId)
                .eq(SocialArticleLike::getUserId, userId));
        if (deleted > 0) {
            incrementArticleStats(articleId, "like_count", -1);
            articleBehaviorProducer.publish(articleId, -1L, 0L, 0L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeComment(Long userId, Long commentId) {
        SocialComment comment = requireComment(commentId);
        Article article = requireArticle(comment.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        assertUserInteractionAllowed(userId, comment.getUserId());
        SocialCommentLike like = new SocialCommentLike();
        like.setCommentId(commentId);
        like.setUserId(userId);
        if (insertIgnoreDuplicate(() -> commentLikeMapper.insert(like))) {
            incrementComment(commentId, "like_count", 1);
            incrementArticleStats(comment.getArticleId(), "comment_like_count", 1);
            articleBehaviorProducer.publish(comment.getArticleId(), 0L, 0L, 0L);
            UserVO actor = currentUser(userId);
            Set<Long> recipients = new LinkedHashSet<>();
            if (!Objects.equals(userId, comment.getUserId())) {
                recipients.add(comment.getUserId());
            }
            if (!Objects.equals(comment.getUserId(), article.getUserId()) && !Objects.equals(userId, article.getUserId())) {
                recipients.add(article.getUserId());
            }
            for (Long recipientId : recipients) {
                notificationEventProducer.publishCommentLike(recipientId, actor, article.getId(), comment.getId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeComment(Long userId, Long commentId) {
        int deleted = commentLikeMapper.delete(new LambdaQueryWrapper<SocialCommentLike>()
                .eq(SocialCommentLike::getCommentId, commentId)
                .eq(SocialCommentLike::getUserId, userId));
        if (deleted > 0) {
            incrementComment(commentId, "like_count", -1);
            SocialComment comment = requireComment(commentId);
            incrementArticleStats(comment.getArticleId(), "comment_like_count", -1);
            articleBehaviorProducer.publish(comment.getArticleId(), 0L, 0L, 0L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeReply(Long userId, Long replyId) {
        SocialReply reply = requireReply(replyId);
        Article article = requireArticle(reply.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        assertUserInteractionAllowed(userId, reply.getUserId());
        if (reply.getReplyToUserId() != null) {
            assertUserInteractionAllowed(userId, reply.getReplyToUserId());
        }
        SocialReplyLike like = new SocialReplyLike();
        like.setReplyId(replyId);
        like.setUserId(userId);
        if (insertIgnoreDuplicate(() -> replyLikeMapper.insert(like))) {
            incrementReply(replyId, "like_count", 1);
            incrementArticleStats(reply.getArticleId(), "reply_like_count", 1);
            articleBehaviorProducer.publish(reply.getArticleId(), 0L, 0L, 0L);
            UserVO actor = currentUser(userId);
            Set<Long> recipients = new LinkedHashSet<>();
            if (!Objects.equals(userId, reply.getUserId())) {
                recipients.add(reply.getUserId());
            }
            if (!Objects.equals(reply.getUserId(), article.getUserId()) && !Objects.equals(userId, article.getUserId())) {
                recipients.add(article.getUserId());
            }
            for (Long recipientId : recipients) {
                notificationEventProducer.publishReplyLike(recipientId, actor, article.getId(), reply.getCommentId(), reply.getId());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeReply(Long userId, Long replyId) {
        int deleted = replyLikeMapper.delete(new LambdaQueryWrapper<SocialReplyLike>()
                .eq(SocialReplyLike::getReplyId, replyId)
                .eq(SocialReplyLike::getUserId, userId));
        if (deleted > 0) {
            incrementReply(replyId, "like_count", -1);
            SocialReply reply = requireReply(replyId);
            incrementArticleStats(reply.getArticleId(), "reply_like_count", -1);
            articleBehaviorProducer.publish(reply.getArticleId(), 0L, 0L, 0L);
        }
    }

    @Override
    public boolean hasLikedArticle(Long userId, Long articleId) {
        return userId != null && articleLikeMapper.selectCount(new LambdaQueryWrapper<SocialArticleLike>()
                .eq(SocialArticleLike::getArticleId, articleId)
                .eq(SocialArticleLike::getUserId, userId)) > 0;
    }

    @Override
    public boolean hasLikedComment(Long userId, Long commentId) {
        return userId != null && commentLikeMapper.selectCount(new LambdaQueryWrapper<SocialCommentLike>()
                .eq(SocialCommentLike::getCommentId, commentId)
                .eq(SocialCommentLike::getUserId, userId)) > 0;
    }

    @Override
    public boolean hasLikedReply(Long userId, Long replyId) {
        return userId != null && replyLikeMapper.selectCount(new LambdaQueryWrapper<SocialReplyLike>()
                .eq(SocialReplyLike::getReplyId, replyId)
                .eq(SocialReplyLike::getUserId, userId)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Article viewArticle(Long userId, Long articleId) {
        Article article = requireArticle(articleId);
        ensureArticleStats(articleId);
        SocialBrowseHistory history = new SocialBrowseHistory();
        history.setUserId(userId);
        history.setArticleId(articleId);
        if (insertIgnoreDuplicate(() -> browseHistoryMapper.insert(history))) {
            incrementArticleStats(articleId, "view_count", 1);
            articleBehaviorProducer.publish(articleId, 0L, 0L, 1L);
        } else {
            browseHistoryMapper.update(null, new LambdaUpdateWrapper<SocialBrowseHistory>()
                    .eq(SocialBrowseHistory::getUserId, userId)
                    .eq(SocialBrowseHistory::getArticleId, articleId)
                    .set(SocialBrowseHistory::getUpdateTime, LocalDateTime.now()));
        }
        return article;
    }

    @Override
    public ArticleStatsVO getArticleStats(Long userId, Long articleId) {
        return toStatsVO(getOrNewStats(articleId), hasLikedArticle(userId, articleId));
    }

    @Override
    public List<ArticleStatsVO> getArticleStatsBatch(Long userId, List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> distinctIds = new ArrayList<>(new LinkedHashSet<>(articleIds));
        Map<Long, SocialArticleStats> statsMap = articleStatsMapper.selectList(new LambdaQueryWrapper<SocialArticleStats>()
                        .in(SocialArticleStats::getArticleId, distinctIds))
                .stream().collect(Collectors.toMap(SocialArticleStats::getArticleId, Function.identity()));
        Set<Long> likedIds = userId == null ? Set.of() : articleLikeMapper.selectList(new LambdaQueryWrapper<SocialArticleLike>()
                        .eq(SocialArticleLike::getUserId, userId)
                        .in(SocialArticleLike::getArticleId, distinctIds))
                .stream().map(SocialArticleLike::getArticleId).collect(Collectors.toSet());
        return distinctIds.stream()
                .map(id -> toStatsVO(statsMap.getOrDefault(id, newStats(id)), likedIds.contains(id)))
                .toList();
    }

    @Override
    public PageResult<BrowseHistoryVO> listBrowseHistory(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialBrowseHistory> result = browseHistoryMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialBrowseHistory>()
                        .eq(SocialBrowseHistory::getUserId, userId)
                        .orderByDesc(SocialBrowseHistory::getUpdateTime));
        List<BrowseHistoryVO> records = result.getRecords().stream().map(item -> {
            BrowseHistoryVO vo = new BrowseHistoryVO();
            vo.setArticleId(item.getArticleId());
            vo.setBrowseTime(item.getUpdateTime() == null ? item.getCreateTime() : item.getUpdateTime());
            vo.setArticle(remoteClient.getArticle(item.getArticleId()));
            return vo;
        }).toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<Article> listLikedArticles(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialArticleLike> result = articleLikeMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialArticleLike>()
                        .eq(SocialArticleLike::getUserId, userId)
                        .orderByDesc(SocialArticleLike::getCreateTime));
        List<Article> records = result.getRecords().stream()
                .map(item -> remoteClient.getArticle(item.getArticleId()))
                .filter(Objects::nonNull)
                .toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<Article> listFeed(Long userId, LocalDateTime before, Long size) {
        long pageSize = normalizeSize(size);
        LocalDateTime cursor = before == null ? LocalDateTime.now().plusSeconds(1) : before;
        List<SocialFeedItem> feedItems = feedItemMapper.selectList(new LambdaQueryWrapper<SocialFeedItem>()
                .eq(SocialFeedItem::getUserId, userId)
                .lt(SocialFeedItem::getPublishedTime, cursor)
                .orderByDesc(SocialFeedItem::getPublishedTime)
                .orderByDesc(SocialFeedItem::getArticleId)
                .last("LIMIT " + pageSize));

        List<Long> articleIds = new ArrayList<>(feedItems.stream().map(SocialFeedItem::getArticleId).toList());
        LocalDateTime fallbackCursor = feedItems.isEmpty()
                ? cursor
                : feedItems.get(feedItems.size() - 1).getPublishedTime();
        if (articleIds.size() < pageSize) {
            List<Long> authorIds = followMapper.selectList(new LambdaQueryWrapper<SocialFollow>()
                            .eq(SocialFollow::getUserId, userId))
                    .stream().map(SocialFollow::getFollowUserId).toList();
            List<Article> fallback = remoteClient.listPublishedByAuthorsBefore(authorIds, fallbackCursor, (int) (pageSize - articleIds.size()));
            for (Article article : fallback) {
                insertFeedItem(userId, article.getUserId(), article.getId(), article.getPublishedTime(), SocialConstants.FeedSourceType.FOLLOW_COMPENSATION);
                articleIds.add(article.getId());
            }
        }

        if (articleIds.isEmpty()) {
            return PageResult.of(List.of(), 1L, pageSize, 0L);
        }
        Map<Long, Article> articleMap = remoteClient.listArticlesByIds(articleIds).stream()
                .collect(Collectors.toMap(Article::getId, Function.identity(), (a, b) -> a));
        List<Article> records = articleIds.stream()
                .distinct()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .toList();
        return PageResult.of(records, 1L, pageSize, (long) records.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishArticleToFollowers(Long authorId, Long articleId, LocalDateTime publishedTime) {
        if (authorId == null || articleId == null || publishedTime == null) {
            return;
        }
        List<SocialFollow> followers = followMapper.selectList(new LambdaQueryWrapper<SocialFollow>()
                .eq(SocialFollow::getFollowUserId, authorId));
        for (SocialFollow follower : followers) {
            if (hasBlackRelation(follower.getUserId(), authorId)) {
                continue;
            }
            if (insertFeedItem(follower.getUserId(), authorId, articleId, publishedTime, SocialConstants.FeedSourceType.PUBLISH_PUSH)) {
                notificationEventProducer.publishFeedUnread(follower.getUserId(), publishedTime);
            }
        }
    }

    private void assertArticleInteractionAllowed(Long userId, Article article) {
        if (userId == null || article == null || Objects.equals(userId, article.getUserId())) {
            return;
        }
        assertUserInteractionAllowed(userId, article.getUserId());
    }

    private void assertUserInteractionAllowed(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null || Objects.equals(userId, targetUserId)) {
            return;
        }
        if (hasBlackRelation(userId, targetUserId)) {
            throw new BusinessException("你与该用户存在黑名单关系，不能进行互动");
        }
    }

    private boolean hasBlackRelation(Long leftUserId, Long rightUserId) {
        if (leftUserId == null || rightUserId == null || Objects.equals(leftUserId, rightUserId)) {
            return false;
        }
        return blackMapper.selectCount(new LambdaQueryWrapper<com.game.community.model.entity.social.SocialBlack>()
                .and(wrapper -> wrapper
                        .eq(com.game.community.model.entity.social.SocialBlack::getUserId, leftUserId)
                        .eq(com.game.community.model.entity.social.SocialBlack::getBlackUserId, rightUserId))
                .or(wrapper -> wrapper
                        .eq(com.game.community.model.entity.social.SocialBlack::getUserId, rightUserId)
                        .eq(com.game.community.model.entity.social.SocialBlack::getBlackUserId, leftUserId))) > 0;
    }

    private Article requireArticle(Long articleId) {
        Article article = remoteClient.getArticle(articleId);
        if (article == null || article.getDeleted() != null && article.getDeleted() == 1) {
            throw new BusinessException("文章不存在");
        }
        return article;
    }

    private SocialComment requireComment(Long commentId) {
        SocialComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() == null || comment.getStatus() != SocialConstants.CommentStatus.NORMAL) {
            throw new BusinessException("评论不存在");
        }
        return comment;
    }

    private SocialReply requireReply(Long replyId) {
        SocialReply reply = replyMapper.selectById(replyId);
        if (reply == null || reply.getStatus() == null || reply.getStatus() != SocialConstants.ReplyStatus.NORMAL) {
            throw new BusinessException("回复不存在");
        }
        return reply;
    }

    private UserVO currentUser(Long userId) {
        return userMap(List.of(userId)).get(userId);
    }

    private Map<Long, UserVO> userMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return remoteClient.listUsersByIds(userIds).stream()
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
    }

    private void ensureArticleStats(Long articleId) {
        if (articleStatsMapper.selectCount(new LambdaQueryWrapper<SocialArticleStats>()
                .eq(SocialArticleStats::getArticleId, articleId)) > 0) {
            return;
        }
        try {
            articleStatsMapper.insert(newStats(articleId));
        } catch (DuplicateKeyException ignored) {
            // 并发创建统计行时，唯一索引会兜底。
        }
    }

    private SocialArticleStats getOrNewStats(Long articleId) {
        SocialArticleStats stats = articleStatsMapper.selectOne(new LambdaQueryWrapper<SocialArticleStats>()
                .eq(SocialArticleStats::getArticleId, articleId));
        return stats == null ? newStats(articleId) : stats;
    }

    private SocialArticleStats newStats(Long articleId) {
        SocialArticleStats stats = new SocialArticleStats();
        stats.setArticleId(articleId);
        stats.setLikeCount(0L);
        stats.setCommentCount(0L);
        stats.setCommentLikeCount(0L);
        stats.setReplyCount(0L);
        stats.setReplyLikeCount(0L);
        stats.setViewCount(0L);
        stats.setVersion(0);
        return stats;
    }

    private void incrementArticleStats(Long articleId, String column, int delta) {
        ensureArticleStats(articleId);
        String expression = delta >= 0
                ? column + " = " + column + " + " + delta
                : column + " = GREATEST(0, " + column + " - " + Math.abs(delta) + ")";
        articleStatsMapper.update(null, new LambdaUpdateWrapper<SocialArticleStats>()
                .eq(SocialArticleStats::getArticleId, articleId)
                .setSql(expression)
                .set(SocialArticleStats::getUpdateTime, LocalDateTime.now()));
    }

    private void incrementComment(Long commentId, String column, int delta) {
        String expression = delta >= 0
                ? column + " = " + column + " + " + delta
                : column + " = GREATEST(0, " + column + " - " + Math.abs(delta) + ")";
        commentMapper.update(null, new LambdaUpdateWrapper<SocialComment>()
                .eq(SocialComment::getId, commentId)
                .setSql(expression)
                .set(SocialComment::getUpdateTime, LocalDateTime.now()));
    }

    private void incrementReply(Long replyId, String column, int delta) {
        String expression = delta >= 0
                ? column + " = " + column + " + " + delta
                : column + " = GREATEST(0, " + column + " - " + Math.abs(delta) + ")";
        replyMapper.update(null, new LambdaUpdateWrapper<SocialReply>()
                .eq(SocialReply::getId, replyId)
                .setSql(expression)
                .set(SocialReply::getUpdateTime, LocalDateTime.now()));
    }

    private Set<Long> likedCommentIds(Long userId, List<Long> commentIds) {
        if (userId == null || commentIds == null || commentIds.isEmpty()) {
            return Set.of();
        }
        return commentLikeMapper.selectList(new LambdaQueryWrapper<SocialCommentLike>()
                        .eq(SocialCommentLike::getUserId, userId)
                        .in(SocialCommentLike::getCommentId, commentIds))
                .stream().map(SocialCommentLike::getCommentId).collect(Collectors.toSet());
    }

    private Set<Long> likedReplyIds(Long userId, List<Long> replyIds) {
        if (userId == null || replyIds == null || replyIds.isEmpty()) {
            return Set.of();
        }
        return replyLikeMapper.selectList(new LambdaQueryWrapper<SocialReplyLike>()
                        .eq(SocialReplyLike::getUserId, userId)
                        .in(SocialReplyLike::getReplyId, replyIds))
                .stream().map(SocialReplyLike::getReplyId).collect(Collectors.toSet());
    }

    private boolean insertIgnoreDuplicate(InsertAction action) {
        try {
            return action.insert() > 0;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    private boolean insertFeedItem(Long userId, Long authorId, Long articleId, LocalDateTime publishedTime, int sourceType) {
        if (userId == null || authorId == null || articleId == null || publishedTime == null) {
            return false;
        }
        SocialFeedItem item = new SocialFeedItem();
        item.setUserId(userId);
        item.setAuthorId(authorId);
        item.setArticleId(articleId);
        item.setPublishedTime(publishedTime);
        item.setSourceType(sourceType);
        return insertIgnoreDuplicate(() -> feedItemMapper.insert(item));
    }

    private CommentVO toCommentVO(SocialComment comment, String content, boolean liked) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setArticleId(comment.getArticleId());
        vo.setUserId(comment.getUserId());
        vo.setUsername(comment.getUsername());
        vo.setAvatar(comment.getAvatar());
        vo.setContent(StringUtils.hasText(content) ? content : "");
        vo.setLikeCount(defaultLong(comment.getLikeCount()));
        vo.setReplyCount(defaultLong(comment.getReplyCount()));
        vo.setLiked(liked);
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    private ReplyVO toReplyVO(SocialReply reply, boolean liked) {
        ReplyVO vo = new ReplyVO();
        vo.setId(reply.getId());
        vo.setCommentId(reply.getCommentId());
        vo.setArticleId(reply.getArticleId());
        vo.setUserId(reply.getUserId());
        vo.setUsername(reply.getUsername());
        vo.setAvatar(reply.getAvatar());
        vo.setReplyToUserId(reply.getReplyToUserId());
        vo.setReplyToUsername(reply.getReplyToUsername());
        vo.setContent(reply.getContent());
        vo.setLikeCount(defaultLong(reply.getLikeCount()));
        vo.setLiked(liked);
        vo.setCreateTime(reply.getCreateTime());
        return vo;
    }

    private ArticleStatsVO toStatsVO(SocialArticleStats stats, boolean liked) {
        ArticleStatsVO vo = new ArticleStatsVO();
        vo.setArticleId(stats.getArticleId());
        vo.setLikeCount(defaultLong(stats.getLikeCount()));
        vo.setCommentCount(defaultLong(stats.getCommentCount()));
        vo.setCommentLikeCount(defaultLong(stats.getCommentLikeCount()));
        vo.setReplyCount(defaultLong(stats.getReplyCount()));
        vo.setReplyLikeCount(defaultLong(stats.getReplyLikeCount()));
        vo.setViewCount(defaultLong(stats.getViewCount()));
        vo.setLiked(liked);
        return vo;
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizeSize(Long size) {
        return size == null || size < 1 ? 20 : Math.min(size, 100);
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }

    @FunctionalInterface
    private interface InsertAction {
        int insert();
    }
}
