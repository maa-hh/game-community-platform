package com.game.community.social.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.vo.social.FollowUserVO;

public interface FollowService {

    void follow(Long userId, Long targetUserId);

    void unfollow(Long userId, Long targetUserId);

    void black(Long userId, Long targetUserId);

    void unblack(Long userId, Long targetUserId);

    boolean isFollowing(Long userId, Long targetUserId);

    boolean isBlacked(Long userId, Long targetUserId);

    PageResult<FollowUserVO> listFollowing(Long currentUserId, Long userId, Long page, Long size);

    PageResult<FollowUserVO> listFans(Long currentUserId, Long userId, Long page, Long size);

    PageResult<FollowUserVO> listBlack(Long userId, Long page, Long size);

    long countFollowing(Long userId);

    long countFans(Long userId);
}
