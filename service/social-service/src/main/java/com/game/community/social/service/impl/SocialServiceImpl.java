package com.game.community.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.constant.content.ContentConstants;
import com.game.community.common.constant.notification.NotificationConstants;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.AddCommentDTO;
import com.game.community.model.dto.social.AddReplyDTO;
import com.game.community.model.dto.social.CommentPageDTO;
import com.game.community.model.dto.social.FeedQueryDTO;
import com.game.community.model.dto.social.ReplyPageDTO;
import com.game.community.model.dto.social.ShareArticleDTO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.entity.social.SocialArticleLike;
import com.game.community.model.entity.social.SocialFavorite;
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
import com.game.community.model.vo.social.MyCommentFeedVO;
import com.game.community.model.vo.social.ReplyVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.social.client.SocialRemoteClient;
import com.game.community.social.mapper.SocialArticleLikeMapper;
import com.game.community.social.mapper.SocialArticleStatsMapper;
import com.game.community.social.mapper.SocialBrowseHistoryMapper;
import com.game.community.social.mapper.SocialCommentLikeMapper;
import com.game.community.social.mapper.SocialCommentMapper;
import com.game.community.social.mapper.SocialFavoriteMapper;
import com.game.community.social.mapper.SocialFeedItemMapper;
import com.game.community.social.mapper.SocialFollowMapper;
import com.game.community.social.mapper.SocialReplyLikeMapper;
import com.game.community.social.mapper.SocialReplyMapper;
import com.game.community.social.mongo.SocialCommentContentRepository;
import com.game.community.social.common.BlackRelationChecker;
import com.game.community.social.event.ArticleBehaviorProducer;
import com.game.community.social.event.NotificationEventProducer;
import com.game.community.social.service.SocialService;
import com.game.community.social.service.SocialStatsCache;
import com.game.community.utils.DfaAuditUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private final BlackRelationChecker blackRelationChecker;
    private final SocialArticleLikeMapper articleLikeMapper;
    private final SocialCommentLikeMapper commentLikeMapper;
    private final SocialReplyLikeMapper replyLikeMapper;
    private final SocialBrowseHistoryMapper browseHistoryMapper;
    private final SocialFavoriteMapper favoriteMapper;
    private final SocialFeedItemMapper feedItemMapper;
    private final SocialFollowMapper followMapper;
    private final SocialCommentContentRepository commentContentRepository;
    private final SocialRemoteClient remoteClient;
    private final DfaAuditUtils dfaAuditUtils;
    private final ArticleBehaviorProducer articleBehaviorProducer;
    private final NotificationEventProducer notificationEventProducer;
    private final SocialStatsCache socialStatsCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addComment(Long userId, AddCommentDTO dto) {
        ArticleListVO article = requirePublishedArticle(dto.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        String contentText = dto.getContent().trim();
        if (!dfaAuditUtils.pass(contentText)) {
            throw new BusinessException("评论包含敏感内容");
        }
        UserCardInternalVO user = currentUser(userId);
        SocialComment comment = new SocialComment();
        comment.setArticleId(article.getId());
        comment.setUserId(userId);
        comment.setUsername(user == null ? "玩家" + userId : user.getUsername());
        comment.setAvatar(user == null || user.getAvatar() == null ? "" : user.getAvatar());
        comment.setLikeCount(0L);
        comment.setReplyCount(0L);
        comment.setStatus(SocialConstants.CommentStatus.NORMAL);
        comment.setVersion(0);
        commentMapper.insert(comment);

        SocialCommentContent content = new SocialCommentContent();
        content.setCommentId(comment.getId());
        content.setArticleId(article.getId());
        content.setUserId(userId);
        content.setContent(contentText);
        content.setCreateTime(LocalDateTime.now());
        content.setUpdateTime(LocalDateTime.now());
        commentContentRepository.save(content);
        incrementArticleStats(article.getId(), "comment_count", 1);
        articleBehaviorProducer.publish(article.getId(), 0L, 1L, 0L);
        if (!Objects.equals(userId, articleAuthorUserId(article))) {
            notificationEventProducer.publishArticleComment(articleAuthorUserId(article), user, article.getId(), comment.getId(), content.getContent());
        }
        notificationEventProducer.publishProfileInvalidation(userId,
                NotificationConstants.ProfileDataDomain.COMMENTS);
        return comment.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        SocialComment comment = requireComment(commentId);
        if (!Objects.equals(comment.getUserId(), userId)) {
            throw new BusinessException("无权删除该评论");
        }
        List<SocialReply> activeReplies = activeReplies(commentId);
        long replyLikeCount = countReplyLikes(activeReplies);
        int updated = commentMapper.update(null, new LambdaUpdateWrapper<SocialComment>()
                .eq(SocialComment::getId, commentId)
                .eq(SocialComment::getUserId, userId)
                .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                .set(SocialComment::getStatus, SocialConstants.CommentStatus.DELETED)
                .set(SocialComment::getDeleted, 1));
        if (updated > 0) {
            hideReplies(activeReplies, SocialConstants.ReplyStatus.DELETED);
            commentContentRepository.deleteByCommentId(commentId);
            incrementArticleStats(comment.getArticleId(), "comment_count", -1);
            incrementArticleStats(comment.getArticleId(), "reply_count", -activeReplies.size());
            incrementArticleStats(comment.getArticleId(), "comment_like_count", -defaultLong(comment.getLikeCount()));
            incrementArticleStats(comment.getArticleId(), "reply_like_count", -replyLikeCount);
            articleBehaviorProducer.publish(comment.getArticleId(), 0L, -1L, 0L);
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.COMMENTS);
        }
    }

    @Override
    public PageResult<CommentVO> listComments(Long userId, CommentPageDTO dto) {
        Long articleId = requirePublishedArticle(dto.getArticleId()).getId();
        long page = normalizePage(dto.getPage());
        long size = normalizeSize(dto.getSize());
        Page<SocialComment> result = commentMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<SocialComment>()
                .eq(SocialComment::getArticleId, articleId)
                .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                .orderByDesc(SocialComment::getCreateTime));
        List<Long> commentIds = result.getRecords().stream().map(SocialComment::getId).toList();
        Map<Long, String> contentMap = commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Set<Long> likedIds = likedCommentIds(userId, commentIds);
        List<SocialComment> commentEntities = result.getRecords();
        Map<Long, UserCardInternalVO> users = userMap(commentEntities.stream()
                .map(SocialComment::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<CommentVO> records = commentEntities.stream()
                .map(item -> toCommentVO(
                        item,
                        contentMap.get(item.getId()),
                        likedIds.contains(item.getId()),
                        users.get(item.getUserId())))
                .toList();
        return PageResult.of(records, page, size, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addReply(Long userId, AddReplyDTO dto) {
        SocialComment comment = requireComment(dto.getCommentId());
        ArticleListVO article = requirePublishedArticle(comment.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        assertUserInteractionAllowed(userId, comment.getUserId());
        String contentText = dto.getContent().trim();
        if (!dfaAuditUtils.pass(contentText)) {
            throw new BusinessException("回复包含敏感内容");
        }
        Long replyToUserId = null;
        if (dto.getReplyToAccountId() != null) {
            UserCardInternalVO replyTo = remoteClient.getUserByAccountId(dto.getReplyToAccountId());
            if (replyTo != null && replyTo.getUserId() != null) {
                replyToUserId = replyTo.getUserId();
                assertReplyTargetBelongsToComment(comment, replyTo.getUserId());
                assertUserInteractionAllowed(userId, replyTo.getUserId());
            } else {
                throw new BusinessException("被回复用户不存在");
            }
        }
        UserCardInternalVO user = currentUser(userId);
        UserCardInternalVO replyToUser = replyToUserId == null ? null : userMap(List.of(replyToUserId)).get(replyToUserId);
        SocialReply reply = new SocialReply();
        reply.setCommentId(comment.getId());
        reply.setArticleId(comment.getArticleId());
        reply.setUserId(userId);
        reply.setUsername(user == null ? "玩家" + userId : user.getUsername());
        reply.setAvatar(user == null || user.getAvatar() == null ? "" : user.getAvatar());
        reply.setReplyToUserId(replyToUserId);
        reply.setReplyToUsername(replyToUser == null ? null : replyToUser.getUsername());
        reply.setContent(contentText);
        reply.setLikeCount(0L);
        reply.setStatus(SocialConstants.ReplyStatus.NORMAL);
        reply.setVersion(0);
        replyMapper.insert(reply);
        incrementComment(comment.getId(), "reply_count", 1);
        incrementArticleStats(comment.getArticleId(), "reply_count", 1);
        articleBehaviorProducer.publish(comment.getArticleId(), 0L, 1L, 0L);
        Set<Long> recipients = new LinkedHashSet<>();
        if (!Objects.equals(userId, articleAuthorUserId(article))) {
            recipients.add(articleAuthorUserId(article));
        }
        if (replyToUserId != null && !Objects.equals(userId, replyToUserId)) {
            recipients.add(replyToUserId);
        } else if (!Objects.equals(userId, comment.getUserId())) {
            recipients.add(comment.getUserId());
        }
        for (Long recipientId : recipients) {
            notificationEventProducer.publishCommentReply(recipientId, user, comment.getArticleId(), comment.getId(), reply.getId(), reply.getContent());
        }
        notificationEventProducer.publishProfileInvalidation(userId,
                NotificationConstants.ProfileDataDomain.COMMENTS);
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
            incrementArticleStats(reply.getArticleId(), "reply_like_count", -defaultLong(reply.getLikeCount()));
            articleBehaviorProducer.publish(reply.getArticleId(), 0L, -1L, 0L);
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.COMMENTS);
        }
    }

    @Override
    public CommentVO getCommentDetail(Long userId, Long commentId) {
        SocialComment comment = requireComment(commentId);
        String content = commentContentRepository.findByCommentId(commentId)
                .map(SocialCommentContent::getContent)
                .orElse("");
        boolean liked = hasLikedComment(userId, commentId);
        Map<Long, UserCardInternalVO> users = userMap(List.of(comment.getUserId()));
        return toCommentVO(comment, content, liked, users.get(comment.getUserId()));
    }

    @Override
    public ReplyVO getReplyDetail(Long userId, Long replyId) {
        SocialReply reply = requireReply(replyId);
        boolean liked = hasLikedReply(userId, replyId);
        return toReplyVO(reply, liked, replyUserMap(List.of(reply)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void hideCommentByAudit(Long commentId) {
        SocialComment comment = requireComment(commentId);
        List<SocialReply> activeReplies = activeReplies(commentId);
        long replyLikeCount = countReplyLikes(activeReplies);
        int updated = commentMapper.update(null, new LambdaUpdateWrapper<SocialComment>()
                .eq(SocialComment::getId, commentId)
                .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                .set(SocialComment::getStatus, SocialConstants.CommentStatus.HIDDEN)
                .set(SocialComment::getUpdateTime, LocalDateTime.now()));
        if (updated > 0) {
            hideReplies(activeReplies, SocialConstants.ReplyStatus.HIDDEN);
            incrementArticleStats(comment.getArticleId(), "comment_count", -1);
            incrementArticleStats(comment.getArticleId(), "reply_count", -activeReplies.size());
            incrementArticleStats(comment.getArticleId(), "comment_like_count", -defaultLong(comment.getLikeCount()));
            incrementArticleStats(comment.getArticleId(), "reply_like_count", -replyLikeCount);
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
            incrementArticleStats(reply.getArticleId(), "reply_like_count", -defaultLong(reply.getLikeCount()));
            articleBehaviorProducer.publish(reply.getArticleId(), 0L, -1L, 0L);
        }
    }

    @Override
    public PageResult<ReplyVO> listReplies(Long userId, ReplyPageDTO dto) {
        SocialComment parent = requireComment(dto.getCommentId());
        requirePublishedArticle(parent.getArticleId());
        long page = normalizePage(dto.getPage());
        long size = normalizeSize(dto.getSize());
        Page<SocialReply> result = replyMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<SocialReply>()
                .eq(SocialReply::getCommentId, dto.getCommentId())
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                .orderByAsc(SocialReply::getCreateTime));
        List<Long> replyIds = result.getRecords().stream().map(SocialReply::getId).toList();
        Set<Long> likedIds = likedReplyIds(userId, replyIds);
        List<SocialReply> replyEntities = result.getRecords();
        Map<Long, UserCardInternalVO> users = replyUserMap(replyEntities);
        List<ReplyVO> records = replyEntities.stream()
                .map(item -> toReplyVO(item, likedIds.contains(item.getId()), users))
                .toList();
        return PageResult.of(records, page, size, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long userId, Long articleId) {
        ArticleListVO article = requirePublishedArticle(articleId);
        assertArticleInteractionAllowed(userId, article);
        SocialArticleLike like = new SocialArticleLike();
        like.setArticleId(articleId);
        like.setUserId(userId);
        if (insertIgnoreDuplicate(() -> articleLikeMapper.insert(like))) {
            incrementArticleStats(articleId, "like_count", 1);
            articleBehaviorProducer.publish(articleId, 1L, 0L, 0L);
            Long authorUserId = articleAuthorUserId(article);
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.LIKED);
            if (!Objects.equals(userId, authorUserId)) {
                notificationEventProducer.publishArticleLike(authorUserId, currentUser(userId), articleId);
                notificationEventProducer.publishProfileInvalidation(authorUserId,
                        NotificationConstants.ProfileDataDomain.STATS,
                        NotificationConstants.ProfileDataDomain.RECEIVED);
            }
        }
    }

    /** 将公开文章标识转换为内部主键后执行点赞，内部主键不经过 Controller。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeArticle(Long userId, String articlePublicId) {
        likeArticle(userId, resolveArticleId(articlePublicId));
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
            ArticleListVO article = requirePublishedArticle(articleId);
            Long authorUserId = articleAuthorUserId(article);
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.LIKED);
            if (!Objects.equals(userId, authorUserId)) {
                notificationEventProducer.publishProfileInvalidation(authorUserId,
                        NotificationConstants.ProfileDataDomain.STATS,
                        NotificationConstants.ProfileDataDomain.RECEIVED);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeArticle(Long userId, String articlePublicId) {
        unlikeArticle(userId, resolveArticleId(articlePublicId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeComment(Long userId, Long commentId) {
        SocialComment comment = requireComment(commentId);
        ArticleListVO article = requirePublishedArticle(comment.getArticleId());
        assertArticleInteractionAllowed(userId, article);
        assertUserInteractionAllowed(userId, comment.getUserId());
        SocialCommentLike like = new SocialCommentLike();
        like.setCommentId(commentId);
        like.setUserId(userId);
        if (insertIgnoreDuplicate(() -> commentLikeMapper.insert(like))) {
            incrementComment(commentId, "like_count", 1);
            incrementArticleStats(comment.getArticleId(), "comment_like_count", 1);
            UserCardInternalVO actor = currentUser(userId);
            Set<Long> recipients = new LinkedHashSet<>();
            if (!Objects.equals(userId, comment.getUserId())) {
                recipients.add(comment.getUserId());
            }
            for (Long recipientId : recipients) {
                notificationEventProducer.publishCommentLike(recipientId, actor, article.getId(), comment.getId());
            }
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.LIKED);
            if (!Objects.equals(userId, comment.getUserId())) {
                notificationEventProducer.publishProfileInvalidation(comment.getUserId(),
                        NotificationConstants.ProfileDataDomain.STATS,
                        NotificationConstants.ProfileDataDomain.RECEIVED);
            }
            articleBehaviorProducer.publishCommentLike(comment.getArticleId(), 1L);
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
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.LIKED);
            if (!Objects.equals(userId, comment.getUserId())) {
                notificationEventProducer.publishProfileInvalidation(comment.getUserId(),
                        NotificationConstants.ProfileDataDomain.STATS,
                        NotificationConstants.ProfileDataDomain.RECEIVED);
            }
            articleBehaviorProducer.publishCommentLike(comment.getArticleId(), -1L);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeReply(Long userId, Long replyId) {
        SocialReply reply = requireReply(replyId);
        ArticleListVO article = requirePublishedArticle(reply.getArticleId());
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
            UserCardInternalVO actor = currentUser(userId);
            Set<Long> recipients = new LinkedHashSet<>();
            if (!Objects.equals(userId, reply.getUserId())) {
                recipients.add(reply.getUserId());
            }
            for (Long recipientId : recipients) {
                notificationEventProducer.publishReplyLike(recipientId, actor, article.getId(), reply.getCommentId(), reply.getId());
            }
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.LIKED);
            if (!Objects.equals(userId, reply.getUserId())) {
                notificationEventProducer.publishProfileInvalidation(reply.getUserId(),
                        NotificationConstants.ProfileDataDomain.STATS,
                        NotificationConstants.ProfileDataDomain.RECEIVED);
            }
            articleBehaviorProducer.publishReplyLike(reply.getArticleId(), 1L);
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
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.LIKED);
            if (!Objects.equals(userId, reply.getUserId())) {
                notificationEventProducer.publishProfileInvalidation(reply.getUserId(),
                        NotificationConstants.ProfileDataDomain.STATS,
                        NotificationConstants.ProfileDataDomain.RECEIVED);
            }
            articleBehaviorProducer.publishReplyLike(reply.getArticleId(), -1L);
        }
    }

    @Override
    public boolean hasLikedArticle(Long userId, Long articleId) {
        return userId != null && articleLikeMapper.selectCount(new LambdaQueryWrapper<SocialArticleLike>()
                .eq(SocialArticleLike::getArticleId, articleId)
                .eq(SocialArticleLike::getUserId, userId)) > 0;
    }

    @Override
    public boolean hasLikedArticle(Long userId, String articlePublicId) {
        return hasLikedArticle(userId, requirePublishedArticle(articlePublicId).getId());
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
    public ArticleListVO viewArticle(Long userId, Long articleId) {
        ArticleListVO article = requirePublishedArticle(articleId);
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
        notificationEventProducer.publishProfileInvalidation(userId,
                NotificationConstants.ProfileDataDomain.HISTORY);
        return article;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArticleListVO viewArticle(Long userId, String articlePublicId) {
        return viewArticle(userId, resolveArticleId(articlePublicId));
    }

    @Override
    public ArticleStatsVO getArticleStats(Long userId, Long articleId) {
        SocialArticleStats cached = socialStatsCache.get(articleId);
        if (cached == null) {
            cached = getOrNewStats(articleId);
            socialStatsCache.put(cached);
        }
        return toStatsVO(
                cached,
                hasLikedArticle(userId, articleId),
                hasFavoritedArticle(userId, articleId));
    }

    @Override
    public ArticleStatsVO getArticleStats(Long userId, String articlePublicId) {
        ArticleStatsVO stats = getArticleStats(userId, requirePublishedArticle(articlePublicId).getId());
        stats.setPublicId(articlePublicId);
        return stats;
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
        Set<Long> favoritedIds = userId == null ? Set.of() : favoriteMapper.selectList(new LambdaQueryWrapper<SocialFavorite>()
                        .eq(SocialFavorite::getUserId, userId)
                        .in(SocialFavorite::getArticleId, distinctIds))
                .stream().map(SocialFavorite::getArticleId).collect(Collectors.toSet());
        return distinctIds.stream()
                .map(id -> toStatsVO(
                        statsMap.getOrDefault(id, newStats(id)),
                        likedIds.contains(id),
                        favoritedIds.contains(id)))
                .toList();
    }

    /** 批量解析公开文章标识，并保持返回顺序与请求顺序一致。 */
    @Override
    public List<ArticleStatsVO> getArticleStatsBatchByPublicIds(Long userId, List<String> articlePublicIds) {
        if (articlePublicIds == null || articlePublicIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, ArticleListVO> articles = remoteClient.listArticlesByPublicIds(articlePublicIds).stream()
                .filter(article -> article != null && article.getPublicId() != null && article.getId() != null)
                .collect(Collectors.toMap(ArticleListVO::getPublicId, Function.identity(), (left, right) -> left));
        List<Long> articleIds = articlePublicIds.stream()
                .map(articles::get)
                .map(article -> {
                    if (article == null) {
                        throw new BusinessException("帖子不存在");
                    }
                    if (!Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
                        throw new BusinessException("帖子不存在");
                    }
                    return article.getId();
                })
                .toList();
        List<ArticleStatsVO> stats = getArticleStatsBatch(userId, articleIds);
        Map<Long, String> publicIdsByArticleId = articles.values().stream()
                .collect(Collectors.toMap(ArticleListVO::getId, ArticleListVO::getPublicId, (left, right) -> left));
        stats.forEach(item -> item.setPublicId(publicIdsByArticleId.get(item.getArticleId())));
        return stats;
    }

    @Override
    public PageResult<BrowseHistoryVO> listBrowseHistory(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialBrowseHistory> result = browseHistoryMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialBrowseHistory>()
                        .eq(SocialBrowseHistory::getUserId, userId)
                        .orderByDesc(SocialBrowseHistory::getUpdateTime));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(result.getRecords().stream()
                .map(SocialBrowseHistory::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<BrowseHistoryVO> records = result.getRecords().stream().map(item -> {
            BrowseHistoryVO vo = new BrowseHistoryVO();
            vo.setArticleId(item.getArticleId());
            vo.setBrowseTime(item.getUpdateTime() == null ? item.getCreateTime() : item.getUpdateTime());
            ArticleListVO article = articleMap.get(item.getArticleId());
            if (article != null && !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
                return null;
            }
            vo.setArticle(article);
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            return vo;
        }).filter(Objects::nonNull).toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<ArticleListVO> listLikedArticles(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialArticleLike> result = articleLikeMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialArticleLike>()
                        .eq(SocialArticleLike::getUserId, userId)
                        .orderByDesc(SocialArticleLike::getCreateTime));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(result.getRecords().stream()
                .map(SocialArticleLike::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<ArticleListVO> records = result.getRecords().stream()
                .map(item -> {
                    ArticleListVO article = articleMap.get(item.getArticleId());
                    if (article != null) {
                        article.setActionTime(item.getCreateTime());
                    }
                    return article;
                })
                .filter(article -> article != null
                        && Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED))
                .toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<ArticleListVO> listReceivedLikedArticles(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        if (userId == null) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        List<SocialArticleLike> ownedLikes = listOwnedReceivedArticleLikes(userId);
        if (ownedLikes.isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        LinkedHashSet<Long> orderedArticleIds = new LinkedHashSet<>();
        for (SocialArticleLike like : ownedLikes) {
            orderedArticleIds.add(like.getArticleId());
        }
        Map<Long, ArticleListVO> articleMap = loadArticleMap(orderedArticleIds);
        List<ArticleListVO> orderedArticles = orderedArticleIds.stream()
                .map(articleMap::get)
                .filter(Objects::nonNull)
                .toList();
        long total = orderedArticles.size();
        int from = (int) ((current - 1) * pageSize);
        if (from >= orderedArticles.size()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, total);
        }
        int to = (int) Math.min(from + pageSize, orderedArticles.size());
        return PageResult.of(orderedArticles.subList(from, to), current, pageSize, total);
    }

    @Override
    public PageResult<MyCommentFeedVO> listReceivedArticleLikes(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        if (userId == null) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        List<SocialArticleLike> ownedLikes = listOwnedReceivedArticleLikes(userId);
        if (ownedLikes.isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        LinkedHashSet<Long> articleIds = new LinkedHashSet<>();
        LinkedHashSet<Long> likerIds = new LinkedHashSet<>();
        ownedLikes.forEach(like -> {
            articleIds.add(like.getArticleId());
            likerIds.add(like.getUserId());
        });
        Map<Long, ArticleListVO> articleMap = loadArticleMap(articleIds);
        Map<Long, UserCardInternalVO> likerMap = userMap(new ArrayList<>(likerIds));
        List<MyCommentFeedVO> records = new ArrayList<>();
        for (SocialArticleLike like : ownedLikes) {
            ArticleListVO article = articleMap.get(like.getArticleId());
            if (!isPublishedArticleOwnedBy(article, userId)) {
                continue;
            }
            UserCardInternalVO liker = likerMap.get(like.getUserId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(like.getId());
            vo.setItemType("article");
            vo.setArticleId(article.getId());
            vo.setArticlePublicId(article.getPublicId());
            vo.setArticleTitle(article.getTitle());
            vo.setContent(StringUtils.hasText(article.getSummary()) ? article.getSummary() : "");
            vo.setLikeCount(0L);
            vo.setLiked(true);
            vo.setCreateTime(like.getCreateTime());
            vo.setAuthorUserId(articleAuthorUserId(article));
            vo.setLikerUserId(like.getUserId());
            vo.setLikerNickname(liker == null ? "玩家" + like.getUserId() : liker.getUsername());
            vo.setLikerAvatar(liker == null ? null : liker.getAvatar());
            vo.setLikerAccountId(liker == null ? null : liker.getAccountId());
            records.add(vo);
        }
        long total = records.size();
        int from = (int) ((current - 1) * pageSize);
        if (from >= records.size()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, total);
        }
        int to = (int) Math.min(from + pageSize, records.size());
        List<MyCommentFeedVO> pageRecords = records.subList(from, to);
        enrichMyCommentFeedUsers(pageRecords);
        return PageResult.of(pageRecords, current, pageSize, total);
    }

    @Override
    public Long countReceivedLikes(Long userId) {
        if (userId == null) {
            return 0L;
        }
        long articleLikes = listOwnedReceivedArticleLikes(userId).size();
        return articleLikes + countReceivedCommentLikes(userId) + countReceivedReplyLikes(userId);
    }

    private long countReceivedCommentLikes(Long userId) {
        List<Long> myCommentIds = commentMapper.selectList(new LambdaQueryWrapper<SocialComment>()
                        .eq(SocialComment::getUserId, userId)
                        .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                        .select(SocialComment::getId))
                .stream()
                .map(SocialComment::getId)
                .toList();
        if (myCommentIds.isEmpty()) {
            return 0L;
        }
        return commentLikeMapper.selectCount(new LambdaQueryWrapper<SocialCommentLike>()
                .in(SocialCommentLike::getCommentId, myCommentIds)
                .ne(SocialCommentLike::getUserId, userId));
    }

    private long countReceivedReplyLikes(Long userId) {
        List<Long> myReplyIds = replyMapper.selectList(new LambdaQueryWrapper<SocialReply>()
                        .eq(SocialReply::getUserId, userId)
                        .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                        .select(SocialReply::getId))
                .stream()
                .map(SocialReply::getId)
                .toList();
        if (myReplyIds.isEmpty()) {
            return 0L;
        }
        return replyLikeMapper.selectCount(new LambdaQueryWrapper<SocialReplyLike>()
                .in(SocialReplyLike::getReplyId, myReplyIds)
                .ne(SocialReplyLike::getUserId, userId));
    }

    private List<SocialArticleLike> listOwnedReceivedArticleLikes(Long userId) {
        List<SocialArticleLike> likes = articleLikeMapper.selectList(new LambdaQueryWrapper<SocialArticleLike>()
                .ne(SocialArticleLike::getUserId, userId)
                .orderByDesc(SocialArticleLike::getCreateTime)
                .orderByDesc(SocialArticleLike::getId));
        if (likes.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> articleIds = likes.stream()
                .map(SocialArticleLike::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(articleIds);
        List<SocialArticleLike> ownedLikes = new ArrayList<>();
        for (SocialArticleLike like : likes) {
            if (isPublishedArticleOwnedBy(articleMap.get(like.getArticleId()), userId)) {
                ownedLikes.add(like);
            }
        }
        return ownedLikes;
    }

    private Map<Long, ArticleListVO> loadArticleMap(Set<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return remoteClient.listArticlesByIds(new ArrayList<>(articleIds)).stream()
                .filter(item -> item.getId() != null)
                .collect(Collectors.toMap(ArticleListVO::getId, Function.identity(), (left, right) -> left));
    }

    private boolean isPublishedArticleOwnedBy(ArticleListVO article, Long userId) {
        return article != null
                && Objects.equals(articleAuthorUserId(article), userId)
                && Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoriteArticle(Long userId, Long articleId) {
        ArticleListVO article = requirePublishedArticle(articleId);
        assertArticleInteractionAllowed(userId, article);
        SocialFavorite favorite = new SocialFavorite();
        favorite.setArticleId(articleId);
        favorite.setUserId(userId);
        if (insertIgnoreDuplicate(() -> favoriteMapper.insert(favorite))) {
            incrementArticleStats(articleId, "favorite_count", 1);
            articleBehaviorProducer.publish(articleId, 0L, 0L, 0L, 1L, 0L);
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.FAVORITES);
            if (!Objects.equals(userId, articleAuthorUserId(article))) {
                notificationEventProducer.publishArticleFavorite(
                        articleAuthorUserId(article), currentUser(userId), articleId);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoriteArticle(Long userId, String articlePublicId) {
        favoriteArticle(userId, resolveArticleId(articlePublicId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoriteArticle(Long userId, Long articleId) {
        int deleted = favoriteMapper.delete(new LambdaQueryWrapper<SocialFavorite>()
                .eq(SocialFavorite::getArticleId, articleId)
                .eq(SocialFavorite::getUserId, userId));
        if (deleted > 0) {
            incrementArticleStats(articleId, "favorite_count", -1);
            articleBehaviorProducer.publish(articleId, 0L, 0L, 0L, -1L, 0L);
            notificationEventProducer.publishProfileInvalidation(userId,
                    NotificationConstants.ProfileDataDomain.FAVORITES);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoriteArticle(Long userId, String articlePublicId) {
        unfavoriteArticle(userId, resolveArticleId(articlePublicId));
    }

    @Override
    public boolean hasFavoritedArticle(Long userId, Long articleId) {
        return userId != null && favoriteMapper.selectCount(new LambdaQueryWrapper<SocialFavorite>()
                .eq(SocialFavorite::getArticleId, articleId)
                .eq(SocialFavorite::getUserId, userId)) > 0;
    }

    @Override
    public boolean hasFavoritedArticle(Long userId, String articlePublicId) {
        return hasFavoritedArticle(userId, requirePublishedArticle(articlePublicId).getId());
    }

    @Override
    public PageResult<ArticleListVO> listFavoritedArticles(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialFavorite> result = favoriteMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialFavorite>()
                        .eq(SocialFavorite::getUserId, userId)
                        .orderByDesc(SocialFavorite::getCreateTime));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(result.getRecords().stream()
                .map(SocialFavorite::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<ArticleListVO> records = result.getRecords().stream()
                .map(item -> {
                    ArticleListVO article = articleMap.get(item.getArticleId());
                    if (article != null) {
                        article.setActionTime(item.getCreateTime());
                    }
                    return article;
                })
                .filter(article -> article != null
                        && Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED))
                .toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shareArticle(Long userId, Long articleId, ShareArticleDTO dto) {
        ArticleListVO article = requirePublishedArticle(articleId);
        assertArticleInteractionAllowed(userId, article);
        if (dto != null && StringUtils.hasText(dto.getChannel())
                && !SocialConstants.SHARE_CHANNELS.contains(dto.getChannel().trim())) {
            throw new BusinessException("分享渠道不合法");
        }
        incrementArticleStats(articleId, "share_count", 1);
        articleBehaviorProducer.publish(articleId, 0L, 0L, 0L, 0L, 1L);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shareArticle(Long userId, String articlePublicId, ShareArticleDTO dto) {
        shareArticle(userId, resolveArticleId(articlePublicId), dto);
    }

    @Override
    public PageResult<ArticleListVO> listFeed(Long userId, FeedQueryDTO query) {
        long pageSize = normalizeSize(query == null ? null : query.getSize());
        LocalDateTime cursor = parseFeedCursor(query == null ? null : query.getBefore());
        boolean withSelf = query == null || query.getIncludeSelf() == null || query.getIncludeSelf();
        Integer postType = query == null ? null : query.getPostType();
        Long cursorArticleId = Long.MAX_VALUE;
        long batchSize = pageSize;
        List<ArticleListVO> records = new ArrayList<>();
        Set<Long> articleIds = new LinkedHashSet<>();

        // 1. 先按同一个时间游标分页读取 Feed 信箱。类型筛选在文章详情补齐后进行，
        //    因此不能只取一页候选数据，否则前面的图文会把视频/转发挤掉。
        LocalDateTime mailboxCursor = cursor;
        Long mailboxArticleId = cursorArticleId;
        boolean mailboxExhausted = false;
        while (!mailboxExhausted && records.size() < pageSize) {
            LocalDateTime queryCursor = mailboxCursor;
            Long queryArticleId = mailboxArticleId;
            List<SocialFeedItem> feedItems = feedItemMapper.selectList(new LambdaQueryWrapper<SocialFeedItem>()
                    .eq(SocialFeedItem::getUserId, userId)
                    .and(wrapper -> wrapper.lt(SocialFeedItem::getPublishedTime, queryCursor)
                            .or(equalTime -> equalTime.eq(SocialFeedItem::getPublishedTime, queryCursor)
                                    .lt(SocialFeedItem::getArticleId, queryArticleId)))
                    .orderByDesc(SocialFeedItem::getPublishedTime)
                    .orderByDesc(SocialFeedItem::getArticleId)
                    .last("LIMIT " + batchSize));
            if (feedItems.isEmpty()) {
                mailboxExhausted = true;
                break;
            }

            Map<Long, ArticleListVO> articleMap = remoteClient.listArticlesByIds(
                            feedItems.stream().map(SocialFeedItem::getArticleId).toList())
                    .stream().collect(Collectors.toMap(ArticleListVO::getId, Function.identity(), (a, b) -> a));
            for (SocialFeedItem feedItem : feedItems) {
                ArticleListVO article = articleMap.get(feedItem.getArticleId());
                if (article == null || (postType != null && !Objects.equals(article.getPostType(), postType))) {
                    continue;
                }
                if (articleIds.add(article.getId())) {
                    records.add(article);
                }
                if (records.size() >= pageSize) {
                    break;
                }
            }

            SocialFeedItem lastItem = feedItems.get(feedItems.size() - 1);
            mailboxCursor = lastItem.getPublishedTime();
            mailboxArticleId = lastItem.getArticleId();
            mailboxExhausted = feedItems.size() < batchSize;
        }

        // 2. 信箱不足一页时，从全部关注人按原始时间游标回源补齐。
        //    回源数据只参与本次返回，不写入信箱，避免绕过各类型容量限制。
        if (records.size() < pageSize) {
            List<Long> authorIds = followMapper.selectList(new LambdaQueryWrapper<SocialFollow>()
                            .eq(SocialFollow::getUserId, userId))
                    .stream().map(SocialFollow::getFollowUserId).toList();
            LocalDateTime fallbackCursor = cursor;
            Long fallbackArticleId = cursorArticleId;
            boolean fallbackExhausted = authorIds.isEmpty();
            while (!fallbackExhausted && records.size() < pageSize) {
                List<ArticleListVO> fallback = remoteClient.listPublishedByAuthorsBefore(
                        authorIds, fallbackCursor, fallbackArticleId, (int) batchSize);
                if (fallback.isEmpty()) {
                    break;
                }
                for (ArticleListVO article : fallback) {
                    if (article == null || article.getId() == null || article.getPublishedTime() == null) {
                        continue;
                    }
                    if (postType == null || Objects.equals(article.getPostType(), postType)) {
                        if (articleIds.add(article.getId())) {
                            records.add(article);
                        }
                    }
                    if (records.size() >= pageSize) {
                        break;
                    }
                }
                ArticleListVO lastArticle = fallback.get(fallback.size() - 1);
                if (lastArticle == null || lastArticle.getPublishedTime() == null || lastArticle.getId() == null) {
                    break;
                }
                fallbackCursor = lastArticle.getPublishedTime();
                fallbackArticleId = lastArticle.getId();
                fallbackExhausted = fallback.size() < batchSize;
            }
        }

        // 3. 保留原有 includeSelf 语义：自己的文章也作为候选，最后和前两类动态统一按时间排序。
        if (withSelf) {
            List<ArticleListVO> selfArticles = remoteClient.listPublishedByAuthor(
                    userId, (int) Math.min(500L, pageSize * 3L));
            for (ArticleListVO article : selfArticles) {
                if (article == null || article.getId() == null || article.getPublishedTime() == null
                        || article.getPublishedTime().isAfter(cursor)
                        || (article.getPublishedTime().equals(cursor) && article.getId() >= cursorArticleId)
                        || (postType != null && !Objects.equals(article.getPostType(), postType))) {
                    continue;
                }
                if (articleIds.add(article.getId())) {
                    records.add(article);
                }
                if (records.size() >= pageSize) {
                    break;
                }
            }
        }

        if (records.isEmpty()) {
            return PageResult.of(List.of(), 1L, pageSize, 0L);
        }
        records = records.stream()
                .sorted((a, b) -> {
                    LocalDateTime left = a.getPublishedTime() == null ? a.getCreateTime() : a.getPublishedTime();
                    LocalDateTime right = b.getPublishedTime() == null ? b.getCreateTime() : b.getPublishedTime();
                    if (left == null && right == null) {
                        return Long.compare(b.getId(), a.getId());
                    }
                    if (left == null) {
                        return 1;
                    }
                    if (right == null) {
                        return -1;
                    }
                    int compared = right.compareTo(left);
                    return compared != 0 ? compared : Long.compare(b.getId(), a.getId());
                })
                .limit(pageSize)
                .toList();
        return PageResult.of(records, 1L, pageSize, (long) records.size());
    }

    @Override
    public PageResult<MyCommentFeedVO> listMyComments(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialComment> result = commentMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialComment>()
                        .eq(SocialComment::getUserId, userId)
                        .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                        .orderByDesc(SocialComment::getCreateTime));
        List<Long> commentIds = result.getRecords().stream().map(SocialComment::getId).toList();
        Map<Long, String> contentMap = commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(result.getRecords().stream()
                .map(SocialComment::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Set<Long> likedIds = likedCommentIds(userId, commentIds);
        List<MyCommentFeedVO> records = result.getRecords().stream().map(item -> {
            ArticleListVO article = articleMap.get(item.getArticleId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(item.getId());
            vo.setArticleId(item.getArticleId());
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            vo.setArticleTitle(article == null ? "帖子" : article.getTitle());
            vo.setContent(contentMap.getOrDefault(item.getId(), ""));
            vo.setLikeCount(defaultLong(item.getLikeCount()));
            vo.setLiked(likedIds.contains(item.getId()));
            vo.setCreateTime(item.getCreateTime());
            vo.setItemType("comment");
            vo.setAuthorNickname(item.getUsername());
            vo.setAuthorUserId(item.getUserId());
            return vo;
        }).toList();
        enrichMyCommentFeedUsers(records);
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<MyCommentFeedVO> listMyReplies(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialReply> result = replyMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialReply>()
                        .eq(SocialReply::getUserId, userId)
                        .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                        .orderByDesc(SocialReply::getCreateTime));
        List<Long> replyIds = result.getRecords().stream().map(SocialReply::getId).toList();
        List<Long> commentIds = result.getRecords().stream().map(SocialReply::getCommentId).distinct().toList();
        Map<Long, SocialComment> commentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(SocialComment::getId, Function.identity()));
        Map<Long, String> commentContentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(result.getRecords().stream()
                .map(SocialReply::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Set<Long> likedIds = likedReplyIds(userId, replyIds);
        List<MyCommentFeedVO> records = result.getRecords().stream().map(item -> {
            SocialComment parent = commentMap.get(item.getCommentId());
            ArticleListVO article = articleMap.get(item.getArticleId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(item.getId());
            vo.setItemType("reply");
            vo.setArticleId(item.getArticleId());
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            vo.setArticleTitle(article == null ? "帖子" : article.getTitle());
            vo.setContent(item.getContent());
            vo.setLikeCount(defaultLong(item.getLikeCount()));
            vo.setLiked(likedIds.contains(item.getId()));
            vo.setCreateTime(item.getCreateTime());
            vo.setParentCommentId(item.getCommentId());
            vo.setParentCommentContent(commentContentMap.getOrDefault(item.getCommentId(), ""));
            String parentNickname = item.getReplyToUsername();
            if (parentNickname == null && parent != null) {
                parentNickname = parent.getUsername();
            }
            vo.setParentUserNickname(parentNickname);
            if (item.getReplyToUserId() != null) {
                vo.setParentUserId(item.getReplyToUserId());
            } else if (parent != null) {
                vo.setParentUserId(parent.getUserId());
            }
            vo.setAuthorNickname(item.getUsername());
            vo.setAuthorUserId(item.getUserId());
            return vo;
        }).toList();
        enrichMyCommentFeedUsers(records);
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<MyCommentFeedVO> listMyLikedComments(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialCommentLike> result = commentLikeMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialCommentLike>()
                        .eq(SocialCommentLike::getUserId, userId)
                        .orderByDesc(SocialCommentLike::getCreateTime));
        if (result.getRecords().isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        List<Long> commentIds = result.getRecords().stream().map(SocialCommentLike::getCommentId).toList();
        Map<Long, SocialComment> commentMap = commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(SocialComment::getId, Function.identity()));
        Map<Long, String> contentMap = commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(result.getRecords().stream()
                .map(like -> commentMap.get(like.getCommentId()))
                .filter(Objects::nonNull)
                .map(SocialComment::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<MyCommentFeedVO> records = new ArrayList<>();
        for (SocialCommentLike like : result.getRecords()) {
            SocialComment comment = commentMap.get(like.getCommentId());
            if (comment == null || !Objects.equals(comment.getStatus(), SocialConstants.CommentStatus.NORMAL)) {
                continue;
            }
            ArticleListVO article = articleMap.get(comment.getArticleId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(comment.getId());
            vo.setItemType("comment");
            vo.setArticleId(comment.getArticleId());
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            vo.setArticleTitle(article == null ? "帖子" : article.getTitle());
            vo.setContent(contentMap.getOrDefault(comment.getId(), ""));
            vo.setLikeCount(defaultLong(comment.getLikeCount()));
            vo.setLiked(true);
            vo.setCreateTime(like.getCreateTime());
            vo.setAuthorNickname(comment.getUsername());
            vo.setAuthorUserId(comment.getUserId());
            records.add(vo);
        }
        enrichMyCommentFeedUsers(records);
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<MyCommentFeedVO> listMyLikedReplies(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialReplyLike> result = replyLikeMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialReplyLike>()
                        .eq(SocialReplyLike::getUserId, userId)
                        .orderByDesc(SocialReplyLike::getCreateTime));
        if (result.getRecords().isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        List<Long> replyIds = result.getRecords().stream().map(SocialReplyLike::getReplyId).toList();
        Map<Long, SocialReply> replyMap = replyMapper.selectBatchIds(replyIds).stream()
                .collect(Collectors.toMap(SocialReply::getId, Function.identity()));
        List<Long> commentIds = replyMap.values().stream().map(SocialReply::getCommentId).distinct().toList();
        Map<Long, SocialComment> commentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(SocialComment::getId, Function.identity()));
        Map<Long, String> commentContentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(replyMap.values().stream()
                .map(SocialReply::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        List<MyCommentFeedVO> records = new ArrayList<>();
        for (SocialReplyLike like : result.getRecords()) {
            SocialReply reply = replyMap.get(like.getReplyId());
            if (reply == null || !Objects.equals(reply.getStatus(), SocialConstants.ReplyStatus.NORMAL)) {
                continue;
            }
            SocialComment parent = commentMap.get(reply.getCommentId());
            ArticleListVO article = articleMap.get(reply.getArticleId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(reply.getId());
            vo.setItemType("reply");
            vo.setArticleId(reply.getArticleId());
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            vo.setArticleTitle(article == null ? "帖子" : article.getTitle());
            vo.setContent(reply.getContent());
            vo.setLikeCount(defaultLong(reply.getLikeCount()));
            vo.setLiked(true);
            vo.setCreateTime(like.getCreateTime());
            vo.setParentCommentId(reply.getCommentId());
            vo.setParentCommentContent(commentContentMap.getOrDefault(reply.getCommentId(), ""));
            String parentNickname = reply.getReplyToUsername();
            if (parentNickname == null && parent != null) {
                parentNickname = parent.getUsername();
            }
            vo.setParentUserNickname(parentNickname);
            if (reply.getReplyToUserId() != null) {
                vo.setParentUserId(reply.getReplyToUserId());
            } else if (parent != null) {
                vo.setParentUserId(parent.getUserId());
            }
            vo.setAuthorNickname(reply.getUsername());
            vo.setAuthorUserId(reply.getUserId());
            records.add(vo);
        }
        enrichMyCommentFeedUsers(records);
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<MyCommentFeedVO> listReceivedComments(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        List<Long> myCommentIds = commentMapper.selectList(new LambdaQueryWrapper<SocialComment>()
                        .eq(SocialComment::getUserId, userId)
                        .eq(SocialComment::getStatus, SocialConstants.CommentStatus.NORMAL)
                        .select(SocialComment::getId))
                .stream()
                .map(SocialComment::getId)
                .toList();
        if (myCommentIds.isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        Page<SocialCommentLike> result = commentLikeMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialCommentLike>()
                        .in(SocialCommentLike::getCommentId, myCommentIds)
                        .orderByDesc(SocialCommentLike::getCreateTime));
        if (result.getRecords().isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        List<Long> commentIds = result.getRecords().stream().map(SocialCommentLike::getCommentId).distinct().toList();
        Map<Long, SocialComment> commentMap = commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(SocialComment::getId, Function.identity()));
        Map<Long, String> contentMap = commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(commentMap.values().stream()
                .map(SocialComment::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, UserCardInternalVO> likerMap = userMap(result.getRecords().stream()
                .map(SocialCommentLike::getUserId)
                .distinct()
                .toList());
        List<MyCommentFeedVO> records = new ArrayList<>();
        for (SocialCommentLike like : result.getRecords()) {
            SocialComment comment = commentMap.get(like.getCommentId());
            if (comment == null || !Objects.equals(comment.getStatus(), SocialConstants.CommentStatus.NORMAL)) {
                continue;
            }
            UserCardInternalVO liker = likerMap.get(like.getUserId());
            ArticleListVO article = articleMap.get(comment.getArticleId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(comment.getId());
            vo.setItemType("comment");
            vo.setArticleId(comment.getArticleId());
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            vo.setArticleTitle(article == null ? "帖子" : article.getTitle());
            vo.setContent(contentMap.getOrDefault(comment.getId(), ""));
            vo.setLikeCount(defaultLong(comment.getLikeCount()));
            vo.setLiked(true);
            vo.setCreateTime(like.getCreateTime());
            vo.setAuthorUserId(comment.getUserId());
            vo.setAuthorNickname(comment.getUsername());
            vo.setLikerUserId(like.getUserId());
            vo.setLikerNickname(liker == null ? "玩家" + like.getUserId() : liker.getUsername());
            vo.setLikerAvatar(liker == null ? null : liker.getAvatar());
            vo.setLikerAccountId(liker == null ? null : liker.getAccountId());
            records.add(vo);
        }
        enrichMyCommentFeedUsers(records);
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<MyCommentFeedVO> listReceivedReplies(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        List<Long> myReplyIds = replyMapper.selectList(new LambdaQueryWrapper<SocialReply>()
                        .eq(SocialReply::getUserId, userId)
                        .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                        .select(SocialReply::getId))
                .stream()
                .map(SocialReply::getId)
                .toList();
        if (myReplyIds.isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        Page<SocialReplyLike> result = replyLikeMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialReplyLike>()
                        .in(SocialReplyLike::getReplyId, myReplyIds)
                        .orderByDesc(SocialReplyLike::getCreateTime));
        if (result.getRecords().isEmpty()) {
            return PageResult.of(Collections.emptyList(), current, pageSize, 0L);
        }
        List<Long> replyIds = result.getRecords().stream().map(SocialReplyLike::getReplyId).distinct().toList();
        Map<Long, SocialReply> replyMap = replyMapper.selectBatchIds(replyIds).stream()
                .collect(Collectors.toMap(SocialReply::getId, Function.identity()));
        List<Long> commentIds = replyMap.values().stream().map(SocialReply::getCommentId).distinct().toList();
        Map<Long, SocialComment> commentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(SocialComment::getId, Function.identity()));
        Map<Long, String> commentContentMap = commentIds.isEmpty()
                ? Collections.emptyMap()
                : commentContentRepository.findByCommentIdIn(commentIds).stream()
                .collect(Collectors.toMap(SocialCommentContent::getCommentId, SocialCommentContent::getContent));
        Map<Long, ArticleListVO> articleMap = loadArticleMap(replyMap.values().stream()
                .map(SocialReply::getArticleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        Map<Long, UserCardInternalVO> likerMap = userMap(result.getRecords().stream()
                .map(SocialReplyLike::getUserId)
                .distinct()
                .toList());
        List<MyCommentFeedVO> records = new ArrayList<>();
        for (SocialReplyLike like : result.getRecords()) {
            SocialReply reply = replyMap.get(like.getReplyId());
            if (reply == null || !Objects.equals(reply.getStatus(), SocialConstants.ReplyStatus.NORMAL)) {
                continue;
            }
            SocialComment parent = commentMap.get(reply.getCommentId());
            UserCardInternalVO liker = likerMap.get(like.getUserId());
            ArticleListVO article = articleMap.get(reply.getArticleId());
            MyCommentFeedVO vo = new MyCommentFeedVO();
            vo.setId(reply.getId());
            vo.setItemType("reply");
            vo.setArticleId(reply.getArticleId());
            vo.setArticlePublicId(article == null ? null : article.getPublicId());
            vo.setArticleTitle(article == null ? "帖子" : article.getTitle());
            vo.setContent(reply.getContent());
            vo.setLikeCount(defaultLong(reply.getLikeCount()));
            vo.setLiked(true);
            vo.setCreateTime(like.getCreateTime());
            vo.setParentCommentId(reply.getCommentId());
            vo.setParentCommentContent(commentContentMap.getOrDefault(reply.getCommentId(), ""));
            String parentNickname = reply.getReplyToUsername();
            if (parentNickname == null && parent != null) {
                parentNickname = parent.getUsername();
            }
            vo.setParentUserNickname(parentNickname);
            if (reply.getReplyToUserId() != null) {
                vo.setParentUserId(reply.getReplyToUserId());
            } else if (parent != null) {
                vo.setParentUserId(parent.getUserId());
            }
            vo.setAuthorUserId(reply.getUserId());
            vo.setAuthorNickname(reply.getUsername());
            vo.setLikerUserId(like.getUserId());
            vo.setLikerNickname(liker == null ? "玩家" + like.getUserId() : liker.getUsername());
            vo.setLikerAvatar(liker == null ? null : liker.getAvatar());
            vo.setLikerAccountId(liker == null ? null : liker.getAccountId());
            records.add(vo);
        }
        enrichMyCommentFeedUsers(records);
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public void publishArticleToFollowers(Long authorId, Long articleId, LocalDateTime publishedTime) {
        if (authorId == null || articleId == null || publishedTime == null) {
            return;
        }
        ArticleListVO article = remoteClient.getArticle(articleId);
        Integer postType = article == null ? null : article.getPostType();
        long pageNo = 1;
        while (true) {
            Page<SocialFollow> page = new Page<>(pageNo++, 500, false);
            List<SocialFollow> followers = followMapper.selectPage(page, new LambdaQueryWrapper<SocialFollow>()
                    .eq(SocialFollow::getFollowUserId, authorId)
                    .orderByAsc(SocialFollow::getId)).getRecords();
            if (followers.isEmpty()) {
                return;
            }
            for (SocialFollow follower : followers) {
                if (hasBlackRelation(follower.getUserId(), authorId)) {
                    continue;
                }
                Long feedItemId = insertFeedItem(follower.getUserId(), authorId, articleId, postType, publishedTime,
                        SocialConstants.FeedSourceType.PUBLISH_PUSH);
                if (feedItemId != null) {
                    notificationEventProducer.publishFeedUnread(follower.getUserId(), feedItemId, publishedTime);
                    notificationEventProducer.publishProfileInvalidation(
                            follower.getUserId(), NotificationConstants.ProfileDataDomain.FEED);
                }
            }
            if (followers.size() < 500) {
                return;
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeArticleFromFeeds(Long articleId) {
        if (articleId == null) {
            return;
        }
        List<Long> recipientIds = feedItemMapper.selectList(new LambdaQueryWrapper<SocialFeedItem>()
                        .select(SocialFeedItem::getUserId)
                        .eq(SocialFeedItem::getArticleId, articleId))
                .stream()
                .map(SocialFeedItem::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        feedItemMapper.delete(new LambdaQueryWrapper<SocialFeedItem>()
                .eq(SocialFeedItem::getArticleId, articleId));
        recipientIds.forEach(recipientId -> notificationEventProducer.publishProfileInvalidation(
                recipientId, NotificationConstants.ProfileDataDomain.FEED));
    }

    private Long articleAuthorUserId(ArticleListVO article) {
        return remoteClient.articleAuthorUserId(article);
    }

    private void assertArticleInteractionAllowed(Long userId, ArticleListVO article) {
        if (userId == null || article == null || Objects.equals(userId, articleAuthorUserId(article))) {
            return;
        }
        assertUserInteractionAllowed(userId, articleAuthorUserId(article));
    }

    private void assertUserInteractionAllowed(Long userId, Long targetUserId) {
        if (userId == null || targetUserId == null || Objects.equals(userId, targetUserId)) {
            return;
        }
        if (hasBlackRelation(userId, targetUserId)) {
            throw new BusinessException("你与该用户存在黑名单关系，不能进行互动");
        }
    }

    private void assertReplyTargetBelongsToComment(SocialComment comment, Long targetUserId) {
        if (Objects.equals(comment.getUserId(), targetUserId)) {
            return;
        }
        boolean isReplyParticipant = replyMapper.selectCount(new LambdaQueryWrapper<SocialReply>()
                .eq(SocialReply::getCommentId, comment.getId())
                .eq(SocialReply::getUserId, targetUserId)
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)) > 0;
        if (!isReplyParticipant) {
            throw new BusinessException("被回复用户不属于该评论会话");
        }
    }

    private boolean hasBlackRelation(Long leftUserId, Long rightUserId) {
        if (leftUserId == null || rightUserId == null || Objects.equals(leftUserId, rightUserId)) {
            return false;
        }
        return blackRelationChecker.hasRelation(leftUserId, rightUserId);
    }

    private ArticleListVO requirePublishedArticle(Long articleId) {
        ArticleListVO article = remoteClient.getArticle(articleId);
        if (article == null) {
            throw new BusinessException("文章不存在");
        }
        if (!Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            throw new BusinessException("文章不存在");
        }
        return article;
    }

    /** 在 Service 边界解析文章 publicId，避免 Controller 接触内容库内部主键。 */
    private ArticleListVO requirePublishedArticle(String articlePublicId) {
        ArticleListVO article = remoteClient.getArticleByPublicId(articlePublicId);
        if (article == null || article.getId() == null
                || !Objects.equals(article.getStatus(), ContentConstants.ArticleStatus.PUBLISHED)) {
            throw new BusinessException("文章不存在");
        }
        return article;
    }

    /** 仅完成 publicId 到内部主键的边界映射，是否已发布由具体业务动作决定。 */
    private Long resolveArticleId(String articlePublicId) {
        ArticleListVO article = remoteClient.getArticleByPublicId(articlePublicId);
        if (article == null || article.getId() == null) {
            throw new BusinessException("文章不存在");
        }
        return article.getId();
    }

    /** 解析公开信息流时间游标，避免 Controller 直接处理日期格式异常。 */
    private LocalDateTime parseFeedCursor(String before) {
        if (!StringUtils.hasText(before)) {
            return LocalDateTime.now().plusSeconds(1);
        }
        try {
            return LocalDateTime.parse(before);
        } catch (RuntimeException e) {
            throw new BusinessException("信息流游标格式不正确");
        }
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
        SocialComment parent = commentMapper.selectById(reply.getCommentId());
        if (parent == null || !Objects.equals(parent.getStatus(), SocialConstants.CommentStatus.NORMAL)) {
            throw new BusinessException("回复不存在");
        }
        return reply;
    }

    private UserCardInternalVO currentUser(Long userId) {
        return userMap(List.of(userId)).get(userId);
    }

    private void enrichMyCommentFeedUsers(List<MyCommentFeedVO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> userIds = new ArrayList<>();
        for (MyCommentFeedVO vo : records) {
            if (vo.getAuthorUserId() != null) {
                userIds.add(vo.getAuthorUserId());
            }
            if (vo.getLikerUserId() != null) {
                userIds.add(vo.getLikerUserId());
            }
            if (vo.getParentUserId() != null) {
                userIds.add(vo.getParentUserId());
            }
        }
        Map<Long, UserCardInternalVO> users = userMap(userIds.stream().distinct().toList());
        for (MyCommentFeedVO vo : records) {
            if (vo.getAuthorUserId() != null) {
                UserCardInternalVO user = users.get(vo.getAuthorUserId());
                if (user != null) {
                    if (vo.getAuthorNickname() == null) {
                        vo.setAuthorNickname(user.getUsername());
                    }
                    vo.setAuthorAvatar(user.getAvatar());
                    vo.setAuthorAccountId(user.getAccountId());
                }
            }
            if (vo.getLikerUserId() != null) {
                UserCardInternalVO user = users.get(vo.getLikerUserId());
                if (user != null) {
                    if (vo.getLikerNickname() == null) {
                        vo.setLikerNickname(user.getUsername());
                    }
                    vo.setLikerAvatar(user.getAvatar());
                    vo.setLikerAccountId(user.getAccountId());
                }
            }
            if (vo.getParentUserId() != null) {
                UserCardInternalVO user = users.get(vo.getParentUserId());
                if (user != null) {
                    if (vo.getParentUserNickname() == null) {
                        vo.setParentUserNickname(user.getUsername());
                    }
                    vo.setParentUserAvatar(user.getAvatar());
                    vo.setParentUserAccountId(user.getAccountId());
                }
            }
        }
    }

    private Map<Long, UserCardInternalVO> replyUserMap(List<SocialReply> replies) {
        if (replies == null || replies.isEmpty()) {
            return Map.of();
        }
        List<Long> userIds = new ArrayList<>();
        for (SocialReply reply : replies) {
            if (reply.getUserId() != null) {
                userIds.add(reply.getUserId());
            }
            if (reply.getReplyToUserId() != null) {
                userIds.add(reply.getReplyToUserId());
            }
        }
        return userMap(userIds.stream().distinct().toList());
    }

    private Map<Long, UserCardInternalVO> userMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return remoteClient.listUsersByIds(userIds).stream()
                .collect(Collectors.toMap(UserCardInternalVO::getUserId, Function.identity(), (a, b) -> a));
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
        stats.setFavoriteCount(0L);
        stats.setShareCount(0L);
        stats.setVersion(0);
        return stats;
    }

    private List<SocialReply> activeReplies(Long commentId) {
        return replyMapper.selectList(new LambdaQueryWrapper<SocialReply>()
                .eq(SocialReply::getCommentId, commentId)
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL));
    }

    private long countReplyLikes(List<SocialReply> replies) {
        if (replies == null || replies.isEmpty()) {
            return 0L;
        }
        return replyLikeMapper.selectCount(new LambdaQueryWrapper<SocialReplyLike>()
                .in(SocialReplyLike::getReplyId, replies.stream().map(SocialReply::getId).toList()));
    }

    private void hideReplies(List<SocialReply> replies, int status) {
        if (replies == null || replies.isEmpty()) {
            return;
        }
        replyMapper.update(null, new LambdaUpdateWrapper<SocialReply>()
                .in(SocialReply::getId, replies.stream().map(SocialReply::getId).toList())
                .eq(SocialReply::getStatus, SocialConstants.ReplyStatus.NORMAL)
                .set(SocialReply::getStatus, status)
                .set(SocialReply::getDeleted, status == SocialConstants.ReplyStatus.DELETED ? 1 : 0)
                .set(SocialReply::getUpdateTime, LocalDateTime.now()));
    }

    private void incrementArticleStats(Long articleId, String column, long delta) {
        validateCounterColumn(column, SocialConstants.ARTICLE_STAT_COLUMNS);
        ensureArticleStats(articleId);
        String expression = delta >= 0
                ? column + " = " + column + " + " + delta
                : column + " = GREATEST(0, " + column + " - " + Math.abs(delta) + ")";
        articleStatsMapper.update(null, new LambdaUpdateWrapper<SocialArticleStats>()
                .eq(SocialArticleStats::getArticleId, articleId)
                .setSql(expression)
                .set(SocialArticleStats::getUpdateTime, LocalDateTime.now()));
        socialStatsCache.evict(articleId);
    }

    private void incrementComment(Long commentId, String column, long delta) {
        validateCounterColumn(column, SocialConstants.COMMENT_COUNTER_COLUMNS);
        String expression = delta >= 0
                ? column + " = " + column + " + " + delta
                : column + " = GREATEST(0, " + column + " - " + Math.abs(delta) + ")";
        commentMapper.update(null, new LambdaUpdateWrapper<SocialComment>()
                .eq(SocialComment::getId, commentId)
                .setSql(expression)
                .set(SocialComment::getUpdateTime, LocalDateTime.now()));
    }

    private void incrementReply(Long replyId, String column, long delta) {
        validateCounterColumn(column, SocialConstants.REPLY_COUNTER_COLUMNS);
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

    private void validateCounterColumn(String column, Set<String> allowedColumns) {
        if (!allowedColumns.contains(column)) {
            throw new IllegalArgumentException("不支持的统计字段: " + column);
        }
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

    private Long insertFeedItem(Long userId, Long authorId, Long articleId, Integer postType,
                                LocalDateTime publishedTime, int sourceType) {
        if (userId == null || authorId == null || articleId == null || postType == null || publishedTime == null) {
            return null;
        }
        SocialFeedItem item = new SocialFeedItem();
        item.setUserId(userId);
        item.setAuthorId(authorId);
        item.setArticleId(articleId);
        item.setPostType(postType);
        item.setPublishedTime(publishedTime);
        item.setSourceType(sourceType);
        Long itemId = insertIgnoreDuplicate(() -> feedItemMapper.insert(item)) ? item.getId() : null;
        trimFeedCategoryCapacity(userId, postType);
        return itemId;
    }

    /** 每个帖子类型独立限容，超出时淘汰该类型最旧的信箱记录。 */
    private void trimFeedCategoryCapacity(Long userId, Integer postType) {
        long count = feedItemMapper.selectCount(new LambdaQueryWrapper<SocialFeedItem>()
                .eq(SocialFeedItem::getUserId, userId)
                .eq(SocialFeedItem::getPostType, postType));
        long overflow = count - ContentConstants.FEED_CAPACITY;
        if (overflow <= 0) {
            return;
        }
        List<Long> oldestIds = feedItemMapper.selectList(new LambdaQueryWrapper<SocialFeedItem>()
                        .select(SocialFeedItem::getId)
                        .eq(SocialFeedItem::getUserId, userId)
                        .eq(SocialFeedItem::getPostType, postType)
                        .orderByAsc(SocialFeedItem::getPublishedTime)
                        .orderByAsc(SocialFeedItem::getArticleId)
                        .last("LIMIT " + overflow))
                .stream().map(SocialFeedItem::getId).toList();
        if (!oldestIds.isEmpty()) {
            feedItemMapper.deleteBatchIds(oldestIds);
        }
    }

    private CommentVO toCommentVO(
            SocialComment comment,
            String content,
            boolean liked,
            UserCardInternalVO user) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setArticleId(comment.getArticleId());
        if (user != null) {
            vo.setAccountId(user.getAccountId());
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        } else {
            vo.setUsername(comment.getUsername());
            vo.setAvatar(comment.getAvatar());
        }
        vo.setContent(StringUtils.hasText(content) ? content : "");
        vo.setLikeCount(defaultLong(comment.getLikeCount()));
        vo.setReplyCount(defaultLong(comment.getReplyCount()));
        vo.setLiked(liked);
        vo.setCreateTime(comment.getCreateTime());
        return vo;
    }

    private ReplyVO toReplyVO(SocialReply reply, boolean liked, Map<Long, UserCardInternalVO> users) {
        ReplyVO vo = new ReplyVO();
        vo.setId(reply.getId());
        vo.setCommentId(reply.getCommentId());
        vo.setArticleId(reply.getArticleId());
        UserCardInternalVO user = users.get(reply.getUserId());
        if (user != null) {
            vo.setAccountId(user.getAccountId());
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        } else {
            vo.setUsername(reply.getUsername());
            vo.setAvatar(reply.getAvatar());
        }
        if (reply.getReplyToUserId() != null) {
            UserCardInternalVO replyTo = users.get(reply.getReplyToUserId());
            if (replyTo != null) {
                vo.setReplyToAccountId(replyTo.getAccountId());
                vo.setReplyToUsername(replyTo.getUsername());
            } else {
                vo.setReplyToUsername(reply.getReplyToUsername());
            }
        }
        vo.setContent(reply.getContent());
        vo.setLikeCount(defaultLong(reply.getLikeCount()));
        vo.setLiked(liked);
        vo.setCreateTime(reply.getCreateTime());
        return vo;
    }

    private ArticleStatsVO toStatsVO(SocialArticleStats stats, boolean liked, boolean favorited) {
        ArticleStatsVO vo = new ArticleStatsVO();
        vo.setArticleId(stats.getArticleId());
        vo.setLikeCount(defaultLong(stats.getLikeCount()));
        vo.setCommentCount(defaultLong(stats.getCommentCount()));
        vo.setCommentLikeCount(defaultLong(stats.getCommentLikeCount()));
        vo.setReplyCount(defaultLong(stats.getReplyCount()));
        vo.setReplyLikeCount(defaultLong(stats.getReplyLikeCount()));
        vo.setViewCount(defaultLong(stats.getViewCount()));
        vo.setFavoriteCount(defaultLong(stats.getFavoriteCount()));
        vo.setShareCount(defaultLong(stats.getShareCount()));
        vo.setLiked(liked);
        vo.setFavorited(favorited);
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
