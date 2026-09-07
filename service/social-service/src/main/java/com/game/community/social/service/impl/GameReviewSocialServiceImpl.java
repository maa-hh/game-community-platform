package com.game.community.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.social.AddGameReviewReplyDTO;
import com.game.community.model.entity.social.SocialGameReview;
import com.game.community.model.entity.social.SocialGameReviewLike;
import com.game.community.model.entity.social.SocialGameReviewReply;
import com.game.community.model.entity.social.SocialGameReviewReplyLike;
import com.game.community.model.mongo.SocialGameReviewContent;
import com.game.community.model.mongo.SocialGameReviewReplyContent;
import com.game.community.model.vo.social.GameReviewReplyVO;
import com.game.community.model.vo.social.GameReviewSocialStatsVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.social.client.SocialRemoteClient;
import com.game.community.social.event.NotificationEventProducer;
import com.game.community.social.mapper.SocialGameReviewLikeMapper;
import com.game.community.social.mapper.SocialGameReviewMapper;
import com.game.community.social.mapper.SocialGameReviewReplyLikeMapper;
import com.game.community.social.mapper.SocialGameReviewReplyMapper;
import com.game.community.social.mongo.SocialGameReviewContentRepository;
import com.game.community.social.mongo.SocialGameReviewReplyContentRepository;
import com.game.community.social.service.GameReviewSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameReviewSocialServiceImpl implements GameReviewSocialService {

    private final SocialGameReviewMapper reviewMapper;
    private final SocialGameReviewLikeMapper reviewLikeMapper;
    private final SocialGameReviewReplyMapper replyMapper;
    private final SocialGameReviewReplyLikeMapper replyLikeMapper;
    private final SocialGameReviewContentRepository reviewContentRepository;
    private final SocialGameReviewReplyContentRepository replyContentRepository;
    private final SocialRemoteClient remoteClient;
    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureReview(String reviewId, Long appId, Long userId, String content, LocalDateTime createTime) {
        if (!StringUtils.hasText(reviewId)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        SocialGameReview review = findReview(reviewId);
        if (review == null) {
            review = new SocialGameReview();
            review.setReviewId(reviewId);
            review.setAppId(appId);
            review.setLikeCount(0L);
            review.setReplyCount(0L);
            review.setStatus(SocialConstants.GameReviewStatus.NORMAL);
            review.setCreateTime(createTime == null ? now : createTime);
            review.setUpdateTime(now);
            try {
                reviewMapper.insert(review);
            } catch (DuplicateKeyException ignored) {
                review = findReview(reviewId);
            }
        } else if (review.getStatus() == null || review.getStatus() != SocialConstants.GameReviewStatus.NORMAL) {
            review.setStatus(SocialConstants.GameReviewStatus.NORMAL);
            review.setUpdateTime(now);
            reviewMapper.updateById(review);
        }
        if (userId != null && !Objects.equals(review.getUserId(), userId)) {
            review.setUserId(userId);
            reviewMapper.updateById(review);
        }
        if (StringUtils.hasText(content) && review != null) {
            SocialGameReviewContent document = reviewContentRepository.findByReviewId(reviewId)
                    .orElseGet(SocialGameReviewContent::new);
            document.setReviewId(reviewId);
            document.setAppId(appId);
            document.setUserId(userId);
            document.setContent(content);
            document.setCreateTime(document.getCreateTime() == null
                    ? (createTime == null ? now : createTime) : document.getCreateTime());
            document.setUpdateTime(now);
            reviewContentRepository.save(document);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeReview(String reviewId) {
        SocialGameReview review = findReview(reviewId);
        if (review == null) {
            return;
        }
        review.setStatus(0);
        review.setUpdateTime(LocalDateTime.now());
        reviewMapper.updateById(review);
    }

    @Override
    public List<GameReviewSocialStatsVO> listStats(List<String> reviewIds, Long userId) {
        if (reviewIds == null || reviewIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = reviewIds.stream().filter(StringUtils::hasText).distinct().toList();
        Map<String, SocialGameReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<SocialGameReview>()
                        .in(SocialGameReview::getReviewId, ids))
                .stream().collect(Collectors.toMap(SocialGameReview::getReviewId, Function.identity(), (a, b) -> a));
        Map<String, String> contents = reviewContentRepository.findByReviewIdIn(ids).stream()
                .collect(Collectors.toMap(SocialGameReviewContent::getReviewId,
                        SocialGameReviewContent::getContent, (a, b) -> a));
        Map<String, Boolean> liked = likedReviewMap(ids, userId);
        return ids.stream().map(id -> {
            GameReviewSocialStatsVO stats = toStats(id, reviews.get(id), liked.getOrDefault(id, false));
            stats.setContent(contents.get(id));
            return stats;
        }).toList();
    }

    @Override
    public PageResult<GameReviewSocialStatsVO> rank(Long appId, Long page, Long size, Long userId) {
        long pageNo = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        Page<SocialGameReview> result = reviewMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SocialGameReview>()
                        .eq(SocialGameReview::getAppId, appId)
                        .eq(SocialGameReview::getStatus, SocialConstants.GameReviewStatus.NORMAL)
                        .orderByDesc(SocialGameReview::getLikeCount)
                        .orderByDesc(SocialGameReview::getCreateTime)
                        .orderByDesc(SocialGameReview::getId));
        Map<String, Boolean> liked = likedReviewMap(
                result.getRecords().stream().map(SocialGameReview::getReviewId).toList(), userId);
        List<GameReviewSocialStatsVO> records = result.getRecords().stream()
                .map(item -> toStats(item.getReviewId(), item, liked.getOrDefault(item.getReviewId(), false)))
                .toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public PageResult<GameReviewReplyVO> listReplies(Long userId, String reviewId, Long page, Long size) {
        long pageNo = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 20 : Math.min(size, 50);
        Page<SocialGameReviewReply> result = replyMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<SocialGameReviewReply>()
                        .eq(SocialGameReviewReply::getReviewId, reviewId)
                        .eq(SocialGameReviewReply::getStatus, SocialConstants.GameReviewStatus.NORMAL)
                        .orderByAsc(SocialGameReviewReply::getCreateTime)
                        .orderByAsc(SocialGameReviewReply::getId));
        List<String> replyIds = result.getRecords().stream().map(SocialGameReviewReply::getReplyId).toList();
        Map<String, String> contents = replyContentRepository.findByReplyIdIn(replyIds).stream()
                .collect(Collectors.toMap(SocialGameReviewReplyContent::getReplyId,
                        SocialGameReviewReplyContent::getContent, (a, b) -> a));
        Map<Long, UserCardInternalVO> users = userMap(result.getRecords().stream()
                .flatMap(item -> java.util.stream.Stream.of(item.getUserId(), item.getReplyToUserId()))
                .toList());
        Map<String, Boolean> liked = likedReplyMap(replyIds, userId);
        List<GameReviewReplyVO> records = result.getRecords().stream().map(item -> {
            GameReviewReplyVO vo = new GameReviewReplyVO();
            UserCardInternalVO user = users.get(item.getUserId());
            vo.setReplyId(item.getReplyId());
            vo.setReviewId(item.getReviewId());
            vo.setAccountId(user == null ? null : user.getAccountId());
            vo.setUsername(user == null ? item.getUsername() : user.getUsername());
            vo.setAvatar(user == null ? item.getAvatar() : user.getAvatar());
            UserCardInternalVO replyTo = users.get(item.getReplyToUserId());
            vo.setReplyToAccountId(replyTo == null ? null : replyTo.getAccountId());
            vo.setReplyToUsername(replyTo == null ? item.getReplyToUsername() : replyTo.getUsername());
            vo.setContent(contents.getOrDefault(item.getReplyId(), ""));
            vo.setLikeCount(item.getLikeCount() == null ? 0L : item.getLikeCount());
            vo.setLiked(liked.getOrDefault(item.getReplyId(), false));
            vo.setCreateTime(item.getCreateTime());
            return vo;
        }).toList();
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addReply(Long userId, String reviewId, AddGameReviewReplyDTO dto) {
        requireUser(userId);
        SocialGameReview review = requireReview(reviewId);
        if (dto == null || !StringUtils.hasText(dto.getContent())) {
            throw new BusinessException("回复内容不能为空");
        }
        UserCardInternalVO user = remoteClient.listUsersByIds(List.of(userId)).stream().findFirst().orElse(null);
        SocialGameReviewReply replyTo = null;
        if (dto.getReplyToReplyId() != null && !dto.getReplyToReplyId().isBlank()) {
            replyTo = requireReply(dto.getReplyToReplyId());
            if (!Objects.equals(replyTo.getReviewId(), reviewId)) {
                throw new BusinessException("回复对象不属于该评价");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String replyId = UUID.randomUUID().toString().replace("-", "");
        SocialGameReviewReply reply = new SocialGameReviewReply();
        reply.setReplyId(replyId);
        reply.setReviewId(reviewId);
        reply.setUserId(userId);
        reply.setReplyToUserId(replyTo == null ? null : replyTo.getUserId());
        reply.setReplyToUsername(replyTo == null ? null : replyTo.getUsername());
        reply.setUsername(user == null ? "玩家" + userId : user.getUsername());
        reply.setAvatar(user == null || user.getAvatar() == null ? "" : user.getAvatar());
        reply.setLikeCount(0L);
        reply.setStatus(SocialConstants.GameReviewStatus.NORMAL);
        reply.setCreateTime(now);
        reply.setUpdateTime(now);
        replyMapper.insert(reply);
        SocialGameReviewReplyContent document = new SocialGameReviewReplyContent();
        document.setReplyId(replyId);
        document.setReviewId(reviewId);
        document.setUserId(userId);
        document.setContent(dto.getContent().trim());
        document.setCreateTime(now);
        document.setUpdateTime(now);
        replyContentRepository.save(document);
        reviewMapper.update(null, new LambdaUpdateWrapper<SocialGameReview>()
                .eq(SocialGameReview::getId, review.getId())
                .setSql("reply_count = reply_count + 1")
                .set(SocialGameReview::getUpdateTime, now));
        Long recipientUserId = replyTo == null ? review.getUserId() : replyTo.getUserId();
        if (recipientUserId != null && !Objects.equals(recipientUserId, userId)) {
            notificationEventProducer.publishGameReviewReply(
                    recipientUserId, user, review.getAppId(), reviewId, replyId, document.getContent());
        }
        return replyId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteReply(Long userId, String replyId) {
        requireUser(userId);
        SocialGameReviewReply reply = replyMapper.selectOne(new LambdaQueryWrapper<SocialGameReviewReply>()
                .eq(SocialGameReviewReply::getReplyId, replyId).eq(SocialGameReviewReply::getStatus, SocialConstants.GameReviewStatus.NORMAL)
                .last("LIMIT 1"));
        if (reply == null) return;
        if (!Objects.equals(reply.getUserId(), userId)) throw new BusinessException("无权删除该回复");
        reply.setStatus(0);
        reply.setUpdateTime(LocalDateTime.now());
        replyMapper.updateById(reply);
        SocialGameReview review = findReview(reply.getReviewId());
        if (review != null) {
            reviewMapper.update(null, new LambdaUpdateWrapper<SocialGameReview>()
                    .eq(SocialGameReview::getId, review.getId())
                    .setSql("reply_count = GREATEST(reply_count - 1, 0)"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeReview(Long userId, String reviewId) {
        requireUser(userId);
        SocialGameReview review = requireReview(reviewId);
        SocialGameReviewLike like = new SocialGameReviewLike();
        like.setReviewId(reviewId);
        like.setUserId(userId);
        like.setCreateTime(LocalDateTime.now());
        try {
            reviewLikeMapper.insert(like);
            reviewMapper.update(null, new LambdaUpdateWrapper<SocialGameReview>()
                    .eq(SocialGameReview::getId, review.getId()).setSql("like_count = like_count + 1"));
            UserCardInternalVO actor = remoteClient.listUsersByIds(List.of(userId)).stream().findFirst().orElse(null);
            if (review.getUserId() != null && !Objects.equals(review.getUserId(), userId)) {
                notificationEventProducer.publishGameReviewLike(
                        review.getUserId(), actor, review.getAppId(), reviewId);
            }
        } catch (DuplicateKeyException ignored) {
            // 幂等点赞：重复点击不重复计数。
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeReview(Long userId, String reviewId) {
        requireUser(userId);
        SocialGameReview review = requireReview(reviewId);
        int deleted = reviewLikeMapper.delete(new LambdaQueryWrapper<SocialGameReviewLike>()
                .eq(SocialGameReviewLike::getReviewId, reviewId).eq(SocialGameReviewLike::getUserId, userId));
        if (deleted > 0) {
            reviewMapper.update(null, new LambdaUpdateWrapper<SocialGameReview>()
                    .eq(SocialGameReview::getId, review.getId())
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void likeReply(Long userId, String replyId) {
        requireUser(userId);
        SocialGameReviewReply reply = requireReply(replyId);
        SocialGameReviewReplyLike like = new SocialGameReviewReplyLike();
        like.setReplyId(replyId);
        like.setUserId(userId);
        like.setCreateTime(LocalDateTime.now());
        try {
            replyLikeMapper.insert(like);
            replyMapper.update(null, new LambdaUpdateWrapper<SocialGameReviewReply>()
                    .eq(SocialGameReviewReply::getId, reply.getId()).setSql("like_count = like_count + 1"));
            UserCardInternalVO actor = remoteClient.listUsersByIds(List.of(userId)).stream().findFirst().orElse(null);
            SocialGameReview review = findReview(reply.getReviewId());
            if (reply.getUserId() != null && review != null && !Objects.equals(reply.getUserId(), userId)) {
                notificationEventProducer.publishGameReviewReplyLike(
                        reply.getUserId(), actor, review.getAppId(),
                        reply.getReviewId(), replyId);
            }
        } catch (DuplicateKeyException ignored) {
            // 幂等点赞。
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlikeReply(Long userId, String replyId) {
        requireUser(userId);
        SocialGameReviewReply reply = requireReply(replyId);
        int deleted = replyLikeMapper.delete(new LambdaQueryWrapper<SocialGameReviewReplyLike>()
                .eq(SocialGameReviewReplyLike::getReplyId, replyId).eq(SocialGameReviewReplyLike::getUserId, userId));
        if (deleted > 0) {
            replyMapper.update(null, new LambdaUpdateWrapper<SocialGameReviewReply>()
                    .eq(SocialGameReviewReply::getId, reply.getId())
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        }
    }

    private SocialGameReview requireReview(String reviewId) {
        SocialGameReview review = findReview(reviewId);
        if (review == null || !Objects.equals(review.getStatus(), SocialConstants.GameReviewStatus.NORMAL)) throw new BusinessException("短评不存在");
        return review;
    }

    private SocialGameReviewReply requireReply(String replyId) {
        SocialGameReviewReply reply = replyMapper.selectOne(new LambdaQueryWrapper<SocialGameReviewReply>()
                .eq(SocialGameReviewReply::getReplyId, replyId).eq(SocialGameReviewReply::getStatus, SocialConstants.GameReviewStatus.NORMAL)
                .last("LIMIT 1"));
        if (reply == null) throw new BusinessException("回复不存在");
        return reply;
    }

    private SocialGameReview findReview(String reviewId) {
        if (!StringUtils.hasText(reviewId)) return null;
        return reviewMapper.selectOne(new LambdaQueryWrapper<SocialGameReview>()
                .eq(SocialGameReview::getReviewId, reviewId).last("LIMIT 1"));
    }

    private Long requireUser(Long userId) {
        if (userId == null) throw new BusinessException("请先登录");
        return userId;
    }

    private GameReviewSocialStatsVO toStats(String reviewId, SocialGameReview review, boolean liked) {
        GameReviewSocialStatsVO vo = new GameReviewSocialStatsVO();
        vo.setReviewId(reviewId);
        vo.setLikeCount(review == null || review.getLikeCount() == null ? 0L : review.getLikeCount());
        vo.setReplyCount(review == null || review.getReplyCount() == null ? 0L : review.getReplyCount());
        vo.setLiked(liked);
        return vo;
    }

    private Map<String, Boolean> likedReviewMap(List<String> ids, Long userId) {
        if (userId == null || ids == null || ids.isEmpty()) return Map.of();
        return reviewLikeMapper.selectList(new LambdaQueryWrapper<SocialGameReviewLike>()
                        .in(SocialGameReviewLike::getReviewId, ids).eq(SocialGameReviewLike::getUserId, userId))
                .stream().collect(Collectors.toMap(SocialGameReviewLike::getReviewId, item -> true, (a, b) -> a));
    }

    private Map<String, Boolean> likedReplyMap(List<String> ids, Long userId) {
        if (userId == null || ids == null || ids.isEmpty()) return Map.of();
        return replyLikeMapper.selectList(new LambdaQueryWrapper<SocialGameReviewReplyLike>()
                        .in(SocialGameReviewReplyLike::getReplyId, ids).eq(SocialGameReviewReplyLike::getUserId, userId))
                .stream().collect(Collectors.toMap(SocialGameReviewReplyLike::getReplyId, item -> true, (a, b) -> a));
    }

    private Map<Long, UserCardInternalVO> userMap(List<Long> ids) {
        List<Long> distinct = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) return Map.of();
        return remoteClient.listUsersByIds(distinct).stream().filter(item -> item.getUserId() != null)
                .collect(Collectors.toMap(UserCardInternalVO::getUserId, Function.identity(), (a, b) -> a));
    }
}
