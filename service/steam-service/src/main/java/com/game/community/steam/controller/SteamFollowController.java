package com.game.community.steam.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.game.BatchAppIdsDTO;
import com.game.community.model.dto.game.FollowGameDTO;
import com.game.community.model.vo.game.UserGameFollowVO;
import com.game.community.steam.service.SteamFollowService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/steam/follows")
@RequiredArgsConstructor
public class SteamFollowController {

    private final SteamFollowService steamFollowService;

    @LoginCheck
    @GetMapping
    public Result<List<UserGameFollowVO>> list() {
        return Result.success(steamFollowService.listFollows(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/check/{appId}")
    public Result<Boolean> check(@PathVariable("appId") Long appId) {
        return Result.success(steamFollowService.isFollowed(UserThreadLocal.getUserId(), appId));
    }

    @LoginCheck
    @PostMapping("/check-batch")
    public Result<Map<Long, Boolean>> checkBatch(@Valid @RequestBody BatchAppIdsDTO dto) {
        return Result.success(steamFollowService.checkFollowBatch(
                UserThreadLocal.getUserId(), dto.getAppIds()));
    }

    @LoginCheck
    @PostMapping
    public Result<Void> follow(@Valid @RequestBody FollowGameDTO dto) {
        steamFollowService.follow(UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/{appId}")
    public Result<Void> unfollow(@PathVariable("appId") Long appId) {
        steamFollowService.unfollow(UserThreadLocal.getUserId(), appId);
        return Result.success(null);
    }

    @LoginCheck
    @PostMapping("/import-steam")
    public Result<Map<String, Integer>> importSteam() {
        int count = steamFollowService.importFromSteam(UserThreadLocal.getUserId());
        return Result.success(Map.of("imported", count));
    }
}
