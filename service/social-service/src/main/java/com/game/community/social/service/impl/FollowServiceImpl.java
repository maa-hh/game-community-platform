package com.game.community.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.entity.social.SocialBlack;
import com.game.community.model.entity.social.SocialFeedItem;
import com.game.community.model.entity.social.SocialFollow;
import com.game.community.model.vo.social.FollowUserVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.social.client.SocialRemoteClient;
import com.game.community.social.common.BlackRelationChecker;
import com.game.community.social.event.NotificationEventProducer;
import com.game.community.social.mapper.SocialBlackMapper;
import com.game.community.social.mapper.SocialFeedItemMapper;
import com.game.community.social.mapper.SocialFollowMapper;
import com.game.community.social.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl implements FollowService {

    private final SocialFollowMapper followMapper;
    private final SocialBlackMapper blackMapper;
    private final BlackRelationChecker blackRelationChecker;
    private final SocialFeedItemMapper feedItemMapper;
    private final SocialRemoteClient remoteClient;
    private final NotificationEventProducer notificationEventProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long userId, Long targetUserId) {
        validateTarget(userId, targetUserId);
        if (hasBlackRelation(userId, targetUserId)) {
            throw new BusinessException("你与该用户存在黑名单关系，不能关注");
        }
        SocialFollow follow = new SocialFollow();
        follow.setUserId(userId);
        follow.setFollowUserId(targetUserId);
        try {
            followMapper.insert(follow);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        try {
                            compensateFeedOnFollow(userId, targetUserId);
                            notificationEventProducer.publishFollow(targetUserId, currentUser(userId));
                        } catch (RuntimeException e) {
                            log.warn("关注后的 Feed 补偿或通知投递失败: userId={}, targetUserId={}",
                                    userId, targetUserId, e);
                        }
                    }
                });
            } else {
                compensateFeedOnFollow(userId, targetUserId);
                notificationEventProducer.publishFollow(targetUserId, currentUser(userId));
            }
        } catch (DuplicateKeyException ignored) {
            // 重复关注保持幂等。
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long userId, Long targetUserId) {
        followMapper.delete(new LambdaQueryWrapper<SocialFollow>()
                .eq(SocialFollow::getUserId, userId)
                .eq(SocialFollow::getFollowUserId, targetUserId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void black(Long userId, Long targetUserId) {
        validateTarget(userId, targetUserId);
        unfollow(userId, targetUserId);
        followMapper.delete(new LambdaQueryWrapper<SocialFollow>()
                .eq(SocialFollow::getUserId, targetUserId)
                .eq(SocialFollow::getFollowUserId, userId));
        blackRelationChecker.evict(userId, targetUserId);
        SocialBlack black = new SocialBlack();
        black.setUserId(userId);
        black.setBlackUserId(targetUserId);
        try {
            blackMapper.insert(black);
        } catch (DuplicateKeyException ignored) {
            // 重复拉黑保持幂等。
        }
        blackRelationChecker.evict(userId, targetUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unblack(Long userId, Long targetUserId) {
        blackMapper.delete(new LambdaQueryWrapper<SocialBlack>()
                .eq(SocialBlack::getUserId, userId)
                .eq(SocialBlack::getBlackUserId, targetUserId));
        blackRelationChecker.evict(userId, targetUserId);
    }

    @Override
    public boolean isFollowing(Long userId, Long targetUserId) {
        return followMapper.selectCount(new LambdaQueryWrapper<SocialFollow>()
                .eq(SocialFollow::getUserId, userId)
                .eq(SocialFollow::getFollowUserId, targetUserId)) > 0;
    }

    @Override
    public boolean isBlacked(Long userId, Long targetUserId) {
        return blackMapper.selectCount(new LambdaQueryWrapper<SocialBlack>()
                .eq(SocialBlack::getUserId, userId)
                .eq(SocialBlack::getBlackUserId, targetUserId)) > 0;
    }

    @Override
    public boolean hasBlackRelation(Long leftUserId, Long rightUserId) {
        return blackRelationChecker.hasRelation(leftUserId, rightUserId);
    }

    @Override
    public PageResult<FollowUserVO> listFollowing(Long currentUserId, Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialFollow> result = followMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialFollow>()
                        .eq(SocialFollow::getUserId, userId)
                        .orderByDesc(SocialFollow::getCreateTime));
        return PageResult.of(toFollowUsers(result.getRecords(), SocialFollow::getFollowUserId), current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<FollowUserVO> listFans(Long currentUserId, Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialFollow> result = followMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialFollow>()
                        .eq(SocialFollow::getFollowUserId, userId)
                        .orderByDesc(SocialFollow::getCreateTime));
        return PageResult.of(toFollowUsers(result.getRecords(), SocialFollow::getUserId), current, pageSize, result.getTotal());
    }

    @Override
    public PageResult<FollowUserVO> listBlack(Long userId, Long page, Long size) {
        long current = normalizePage(page);
        long pageSize = normalizeSize(size);
        Page<SocialBlack> result = blackMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<SocialBlack>()
                        .eq(SocialBlack::getUserId, userId)
                        .orderByDesc(SocialBlack::getCreateTime));
        List<Long> ids = result.getRecords().stream().map(SocialBlack::getBlackUserId).toList();
        Map<Long, UserCardInternalVO> users = remoteClient.listUsersByIds(ids).stream()
                .collect(Collectors.toMap(UserCardInternalVO::getUserId, Function.identity(), (a, b) -> a));
        List<FollowUserVO> records = result.getRecords().stream()
                .map(item -> toFollowUserVO(users.get(item.getBlackUserId()), item.getCreateTime()))
                .filter(vo -> vo.getAccountId() != null)
                .toList();
        return PageResult.of(records, current, pageSize, result.getTotal());
    }

    @Override
    public long countFollowing(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<SocialFollow>().eq(SocialFollow::getUserId, userId));
    }

    @Override
    public long countFans(Long userId) {
        return followMapper.selectCount(new LambdaQueryWrapper<SocialFollow>().eq(SocialFollow::getFollowUserId, userId));
    }

    @Override
    public void followByAccountId(Long userId, Long targetAccountId) {
        follow(userId, requireUserIdByAccountId(targetAccountId));
    }

    @Override
    public void unfollowByAccountId(Long userId, Long targetAccountId) {
        unfollow(userId, requireUserIdByAccountId(targetAccountId));
    }

    @Override
    public void blackByAccountId(Long userId, Long targetAccountId) {
        black(userId, requireUserIdByAccountId(targetAccountId));
    }

    @Override
    public void unblackByAccountId(Long userId, Long targetAccountId) {
        unblack(userId, requireUserIdByAccountId(targetAccountId));
    }

    @Override
    public boolean isFollowingByAccountId(Long userId, Long targetAccountId) {
        return isFollowing(userId, requireUserIdByAccountId(targetAccountId));
    }

    @Override
    public boolean isBlackedByAccountId(Long userId, Long targetAccountId) {
        return isBlacked(userId, requireUserIdByAccountId(targetAccountId));
    }

    @Override
    public long countFollowingByAccountId(Long accountId) {
        return countFollowing(requireUserIdByAccountId(accountId));
    }

    @Override
    public long countFansByAccountId(Long accountId) {
        return countFans(requireUserIdByAccountId(accountId));
    }

    private Long requireUserIdByAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException("目标用户不能为空");
        }
        UserCardInternalVO user = remoteClient.getUserByAccountId(accountId);
        if (user == null || user.getUserId() == null) {
            throw new BusinessException("目标用户不存在");
        }
        return user.getUserId();
    }

    private List<FollowUserVO> toFollowUsers(List<SocialFollow> follows, Function<SocialFollow, Long> idExtractor) {
        List<Long> ids = follows.stream().map(idExtractor).toList();
        Map<Long, UserCardInternalVO> users = remoteClient.listUsersByIds(ids).stream()
                .collect(Collectors.toMap(UserCardInternalVO::getUserId, Function.identity(), (a, b) -> a));
        return follows.stream()
                .map(item -> toFollowUserVO(users.get(idExtractor.apply(item)), item.getCreateTime()))
                .filter(vo -> vo.getAccountId() != null)
                .toList();
    }

    private FollowUserVO toFollowUserVO(UserCardInternalVO user, java.time.LocalDateTime createTime) {
        FollowUserVO vo = new FollowUserVO();
        if (user == null) {
            return vo;
        }
        vo.setAccountId(user.getAccountId());
        vo.setUsername(user.getUsername());
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setCreateTime(createTime);
        return vo;
    }

    private void validateTarget(Long userId, Long targetUserId) {
        if (targetUserId == null) {
            throw new BusinessException("目标用户不能为空");
        }
        if (targetUserId.equals(userId)) {
            throw new BusinessException("不能操作自己");
        }
        if (remoteClient.listUsersByIds(List.of(targetUserId)).isEmpty()) {
            throw new BusinessException("目标用户不存在");
        }
    }

    private void compensateFeedOnFollow(Long userId, Long targetUserId) {
        List<ArticleListVO> articles = remoteClient.listPublishedByAuthor(targetUserId, 50);
        for (ArticleListVO article : articles) {
            SocialFeedItem item = new SocialFeedItem();
            item.setUserId(userId);
            item.setAuthorId(targetUserId);
            item.setArticleId(article.getId());
            item.setPublishedTime(article.getPublishedTime());
            item.setSourceType(SocialConstants.FeedSourceType.FOLLOW_COMPENSATION);
            try {
                feedItemMapper.insert(item);
                notificationEventProducer.publishFeedUnread(userId, article.getPublishedTime());
            } catch (DuplicateKeyException ignored) {
                // 关注补偿可重复执行，唯一索引保证信箱不重复。
            }
        }
    }

    private UserCardInternalVO currentUser(Long userId) {
        return remoteClient.listUsersByIds(List.of(userId)).stream().findFirst().orElse(null);
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizeSize(Long size) {
        return size == null || size < 1 ? 20 : Math.min(size, 100);
    }
}
