package com.game.community.social.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.entity.article.Article;
import com.game.community.model.entity.social.SocialBlack;
import com.game.community.model.entity.social.SocialFeedItem;
import com.game.community.model.entity.social.SocialFollow;
import com.game.community.model.vo.social.FollowUserVO;
import com.game.community.model.vo.user.UserVO;
import com.game.community.social.client.SocialRemoteClient;
import com.game.community.social.event.NotificationEventProducer;
import com.game.community.social.mapper.SocialBlackMapper;
import com.game.community.social.mapper.SocialFeedItemMapper;
import com.game.community.social.mapper.SocialFollowMapper;
import com.game.community.social.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final SocialFollowMapper followMapper;
    private final SocialBlackMapper blackMapper;
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
            compensateFeedOnFollow(userId, targetUserId);
            notificationEventProducer.publishFollow(targetUserId, currentUser(userId));
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
        SocialBlack black = new SocialBlack();
        black.setUserId(userId);
        black.setBlackUserId(targetUserId);
        try {
            blackMapper.insert(black);
        } catch (DuplicateKeyException ignored) {
            // 重复拉黑保持幂等。
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unblack(Long userId, Long targetUserId) {
        blackMapper.delete(new LambdaQueryWrapper<SocialBlack>()
                .eq(SocialBlack::getUserId, userId)
                .eq(SocialBlack::getBlackUserId, targetUserId));
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

    private boolean hasBlackRelation(Long leftUserId, Long rightUserId) {
        if (leftUserId == null || rightUserId == null || leftUserId.equals(rightUserId)) {
            return false;
        }
        return blackMapper.selectCount(new LambdaQueryWrapper<SocialBlack>()
                .and(wrapper -> wrapper
                        .eq(SocialBlack::getUserId, leftUserId)
                        .eq(SocialBlack::getBlackUserId, rightUserId))
                .or(wrapper -> wrapper
                        .eq(SocialBlack::getUserId, rightUserId)
                        .eq(SocialBlack::getBlackUserId, leftUserId))) > 0;
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
        Map<Long, UserVO> users = remoteClient.listUsersByIds(ids).stream()
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
        List<FollowUserVO> records = result.getRecords().stream()
                .map(item -> toFollowUserVO(users.get(item.getBlackUserId()), item.getCreateTime()))
                .filter(vo -> vo.getUserId() != null)
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

    private List<FollowUserVO> toFollowUsers(List<SocialFollow> follows, Function<SocialFollow, Long> idExtractor) {
        List<Long> ids = follows.stream().map(idExtractor).toList();
        Map<Long, UserVO> users = remoteClient.listUsersByIds(ids).stream()
                .collect(Collectors.toMap(UserVO::getId, Function.identity(), (a, b) -> a));
        return follows.stream()
                .map(item -> toFollowUserVO(users.get(idExtractor.apply(item)), item.getCreateTime()))
                .filter(vo -> vo.getUserId() != null)
                .toList();
    }

    private FollowUserVO toFollowUserVO(UserVO user, java.time.LocalDateTime createTime) {
        FollowUserVO vo = new FollowUserVO();
        if (user == null) {
            return vo;
        }
        vo.setUserId(user.getId());
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
        List<Article> articles = remoteClient.listPublishedByAuthor(targetUserId, 50);
        for (Article article : articles) {
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

    private UserVO currentUser(Long userId) {
        return remoteClient.listUsersByIds(List.of(userId)).stream().findFirst().orElse(null);
    }

    private long normalizePage(Long page) {
        return page == null || page < 1 ? 1 : page;
    }

    private long normalizeSize(Long size) {
        return size == null || size < 1 ? 20 : Math.min(size, 100);
    }
}
