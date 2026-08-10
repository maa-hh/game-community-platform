package com.game.community.steam.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.game.BatchAppIdsDTO;
import com.game.community.model.dto.game.FollowGameDTO;
import com.game.community.model.vo.game.UserGameFollowVO;
import com.game.community.steam.service.SteamFollowService;
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

    /** 查询当前用户关注的游戏。 */
    @LoginCheck
    @GetMapping
    public Result<List<UserGameFollowVO>> list() {
        return Result.success(steamFollowService.listFollows());
    }

    /** 查询当前用户是否关注指定游戏。 */
    @LoginCheck
    @GetMapping("/check/{appId}")
    public Result<Boolean> check(@PathVariable("appId") Long appId) {
        return Result.success(steamFollowService.isFollowed(appId));
    }

    /** 批量查询当前用户的游戏关注状态。 */
    @LoginCheck
    @PostMapping("/check-batch")
    public Result<Map<Long, Boolean>> checkBatch(@Valid @RequestBody BatchAppIdsDTO dto) {
        return Result.success(steamFollowService.checkFollowBatch(
                dto.getAppIds()));
    }

    /** 保存当前用户对游戏的关注关系。 */
    @LoginCheck
    @PostMapping
    public Result<Void> follow(@Valid @RequestBody FollowGameDTO dto) {
        steamFollowService.follow(dto);
        return Result.success(null);
    }

    /** 删除当前用户对指定游戏的关注关系。 */
    @LoginCheck
    @DeleteMapping("/{appId}")
    public Result<Void> unfollow(@PathVariable("appId") Long appId) {
        steamFollowService.unfollow(appId);
        return Result.success(null);
    }

    /** 将当前用户 Steam 游戏库导入为关注关系。 */
    @LoginCheck
    @PostMapping("/import-steam")
    public Result<Map<String, Integer>> importSteam() {
        int count = steamFollowService.importFromSteam();
        return Result.success(Map.of("imported", count));
    }
}
