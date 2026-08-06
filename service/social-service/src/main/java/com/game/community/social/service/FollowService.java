package com.game.community.social.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.vo.social.FollowUserVO;

public interface FollowService {

    void follow(Long userId, Long targetUserId);

    void followByAccountId(Long userId, Long targetAccountId);

    void unfollow(Long userId, Long targetUserId);

    void unfollowByAccountId(Long userId, Long targetAccountId);

    void black(Long userId, Long targetUserId);

    void blackByAccountId(Long userId, Long targetAccountId);

    void unblack(Long userId, Long targetUserId);

    void unblackByAccountId(Long userId, Long targetAccountId);

    boolean isFollowing(Long userId, Long targetUserId);

    boolean isFollowingByAccountId(Long userId, Long targetAccountId);

    boolean isBlacked(Long userId, Long targetUserId);

    boolean isBlackedByAccountId(Long userId, Long targetAccountId);

    boolean hasBlackRelation(Long leftUserId, Long rightUserId);

    PageResult<FollowUserVO> listFollowing(Long currentUserId, Long userId, Long page, Long size);

    PageResult<FollowUserVO> listFans(Long currentUserId, Long userId, Long page, Long size);

    PageResult<FollowUserVO> listBlack(Long userId, Long page, Long size);

    long countFollowing(Long userId);

    long countFans(Long userId);

    long countFollowingByAccountId(Long accountId);

    long countFansByAccountId(Long accountId);
}
