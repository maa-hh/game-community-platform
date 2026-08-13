package com.game.community.social.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.common.constant.social.SocialRateLimitConstants;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.vo.social.FollowUserVO;
import com.game.community.social.service.FollowService;
import com.game.community.social.aspect.SocialRateLimit;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/social/follow")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.FOLLOW,
            limit = SocialRateLimitConstants.FOLLOW_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/by-account/{targetAccountId}")
    public Result<Void> followByAccount(@PathVariable("targetAccountId") Long targetAccountId) {
        followService.followByAccountId(UserThreadLocal.getUserId(), targetAccountId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/by-account/{targetAccountId}")
    public Result<Void> unfollowByAccount(@PathVariable("targetAccountId") Long targetAccountId) {
        followService.unfollowByAccountId(UserThreadLocal.getUserId(), targetAccountId);
        return Result.success(null);
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.FOLLOW,
            limit = SocialRateLimitConstants.FOLLOW_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/{targetUserId}")
    public Result<Void> follow(@PathVariable("targetUserId") Long targetUserId) {
        followService.follow(UserThreadLocal.getUserId(), targetUserId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/{targetUserId}")
    public Result<Void> unfollow(@PathVariable("targetUserId") Long targetUserId) {
        followService.unfollow(UserThreadLocal.getUserId(), targetUserId);
        return Result.success(null);
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.BLACK,
            limit = SocialRateLimitConstants.FOLLOW_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/black/by-account/{targetAccountId}")
    public Result<Void> blackByAccount(@PathVariable("targetAccountId") Long targetAccountId) {
        followService.blackByAccountId(UserThreadLocal.getUserId(), targetAccountId);
        return Result.success(null);
    }

    @LoginCheck
    @SocialRateLimit(action = SocialRateLimitConstants.BLACK,
            limit = SocialRateLimitConstants.FOLLOW_LIMIT,
            windowSeconds = SocialRateLimitConstants.SHORT_WINDOW_SECONDS)
    @PostMapping("/black/{targetUserId}")
    public Result<Void> black(@PathVariable("targetUserId") Long targetUserId) {
        followService.black(UserThreadLocal.getUserId(), targetUserId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/black/by-account/{targetAccountId}")
    public Result<Void> unblackByAccount(@PathVariable("targetAccountId") Long targetAccountId) {
        followService.unblackByAccountId(UserThreadLocal.getUserId(), targetAccountId);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/black/{targetUserId}")
    public Result<Void> unblack(@PathVariable("targetUserId") Long targetUserId) {
        followService.unblack(UserThreadLocal.getUserId(), targetUserId);
        return Result.success(null);
    }

    @LoginCheck
    @GetMapping("/list")
    public PageResult<FollowUserVO> listFollowing(@RequestParam(value = "userId", required = false) Long userId,
                                                  @RequestParam(value = "page", defaultValue = "1") Long page,
                                                  @RequestParam(value = "size", defaultValue = "20") Long size) {
        Long targetUserId = userId == null ? UserThreadLocal.getUserId() : userId;
        return followService.listFollowing(UserThreadLocal.getUserId(), targetUserId, page, size);
    }

    @LoginCheck
    @GetMapping("/fans")
    public PageResult<FollowUserVO> listFans(@RequestParam(value = "userId", required = false) Long userId,
                                             @RequestParam(value = "page", defaultValue = "1") Long page,
                                             @RequestParam(value = "size", defaultValue = "20") Long size) {
        Long targetUserId = userId == null ? UserThreadLocal.getUserId() : userId;
        return followService.listFans(UserThreadLocal.getUserId(), targetUserId, page, size);
    }

    @LoginCheck
    @GetMapping("/black/list")
    public PageResult<FollowUserVO> listBlack(@RequestParam(value = "page", defaultValue = "1") Long page,
                                              @RequestParam(value = "size", defaultValue = "20") Long size) {
        return followService.listBlack(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/check/by-account/{targetAccountId}")
    public Result<Boolean> isFollowingByAccount(@PathVariable("targetAccountId") Long targetAccountId) {
        return Result.success(followService.isFollowingByAccountId(UserThreadLocal.getUserId(), targetAccountId));
    }

    @LoginCheck
    @GetMapping("/check/{targetUserId}")
    public Result<Boolean> isFollowing(@PathVariable("targetUserId") Long targetUserId) {
        return Result.success(followService.isFollowing(UserThreadLocal.getUserId(), targetUserId));
    }

    @LoginCheck
    @GetMapping("/black/check/by-account/{targetAccountId}")
    public Result<Boolean> isBlackedByAccount(@PathVariable("targetAccountId") Long targetAccountId) {
        return Result.success(followService.isBlackedByAccountId(UserThreadLocal.getUserId(), targetAccountId));
    }

    @LoginCheck
    @GetMapping("/black/check/{targetUserId}")
    public Result<Boolean> isBlacked(@PathVariable("targetUserId") Long targetUserId) {
        return Result.success(followService.isBlacked(UserThreadLocal.getUserId(), targetUserId));
    }

    @LoginCheck
    @GetMapping("/count/by-account/{accountId}")
    public Result<Map<String, Long>> countByAccount(@PathVariable("accountId") Long accountId) {
        return Result.success(Map.of(
                "following", followService.countFollowingByAccountId(accountId),
                "fans", followService.countFansByAccountId(accountId)
        ));
    }

    @LoginCheck
    @GetMapping("/count/{userId}")
    public Result<Map<String, Long>> count(@PathVariable("userId") Long userId) {
        return Result.success(Map.of(
                "following", followService.countFollowing(userId),
                "fans", followService.countFans(userId)
        ));
    }
}
