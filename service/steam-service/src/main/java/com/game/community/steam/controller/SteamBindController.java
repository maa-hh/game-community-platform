package com.game.community.steam.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.dto.steam.SteamCallbackDTO;
import com.game.community.model.dto.steam.SteamLibrarySyncQuery;
import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.game.SteamGameStatsVO;
import com.game.community.model.vo.game.SteamGameVO;
import com.game.community.model.vo.game.SteamLibrarySyncVO;
import com.game.community.steam.config.SteamProperties;
import com.game.community.steam.service.SteamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/steam")
@RequiredArgsConstructor
@Slf4j
public class SteamBindController {

    private final SteamService steamService;
    private final SteamProperties steamProperties;

    /** 生成当前用户发起 Steam 绑定所需的授权地址。 */
    @LoginCheck
    @GetMapping("/auth-url")
    public Result<Map<String, String>> authUrl() {
        String url = steamService.authUrl();
        return Result.success(Map.of("url", url));
    }

    /** 接收 Steam OpenID 回调并将结果重定向回前端。 */
    @GetMapping("/callback")
    public RedirectView callback(@RequestParam Map<String, String> params) {
        String redirectBase = steamProperties.getFrontendRedirect();
        String separator = redirectBase.contains("?") ? "&" : "?";
        try {
            steamService.callback(new SteamCallbackDTO(params.get("state"), params));
            return new RedirectView(redirectBase + separator + "steam=success");
        } catch (Exception e) {
            log.warn("Steam 绑定回调处理失败", e);
            return new RedirectView(redirectBase + separator + "steam=fail");
        }
    }

    /** 查询当前用户的 Steam 绑定资料。 */
    @LoginCheck
    @GetMapping("/profile")
    public Result<SteamBindVO> profile() {
        return Result.success(steamService.profile());
    }

    /** 按 accountId 查询其他用户的 Steam 绑定资料。 */
    @LoginCheck
    @GetMapping("/users/by-account/{accountId}/profile")
    public Result<SteamBindVO> profileForUserByAccount(@PathVariable("accountId") Long accountId) {
        return Result.success(steamService.profileForViewerByAccount(accountId));
    }

    /** 查询当前用户的 Steam 游戏库。 */
    @LoginCheck
    @GetMapping("/library")
    public Result<List<SteamGameVO>> library() {
        return Result.success(steamService.library());
    }

    /** 按 accountId 查询其他用户公开的 Steam 游戏库。 */
    @LoginCheck
    @GetMapping("/users/by-account/{accountId}/library")
    public Result<List<SteamGameVO>> libraryForUserByAccount(@PathVariable("accountId") Long accountId) {
        return Result.success(steamService.libraryForViewerByAccount(accountId));
    }

    /** 查询当前用户指定游戏的 Steam 统计信息。 */
    @LoginCheck
    @GetMapping("/games/{appId}/stats")
    public Result<SteamGameStatsVO> gameStats(@PathVariable("appId") Long appId) {
        return Result.success(steamService.gameStats(appId));
    }

    /** 手动刷新当前用户指定游戏的成就，实际请求由后台线程池执行。 */
    @LoginCheck
    @PostMapping("/games/{appId}/achievements/sync")
    public Result<SteamGameStatsVO> syncAchievements(@PathVariable("appId") Long appId) {
        return Result.success(steamService.syncAchievements(appId));
    }

    /** 分页增量同步当前用户的 Steam 游戏库，每次最多处理 20 个游戏。 */
    @LoginCheck
    @PostMapping("/sync")
    public Result<SteamLibrarySyncVO> sync(
            @Valid @ModelAttribute SteamLibrarySyncQuery query) {
        return Result.success(steamService.syncLibrary(query));
    }

    /** 解绑当前用户的 Steam 账号。 */
    @LoginCheck
    @DeleteMapping("/unbind")
    public Result<Void> unbind() {
        steamService.unbind();
        return Result.success(null);
    }
}
