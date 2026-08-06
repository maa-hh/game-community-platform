package com.game.community.steam.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.Result;
import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.game.SteamGameStatsVO;
import com.game.community.model.vo.game.SteamGameVO;
import com.game.community.steam.config.SteamProperties;
import com.game.community.steam.service.SteamService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/steam")
@RequiredArgsConstructor
public class SteamBindController {

    private final SteamService steamService;
    private final SteamProperties steamProperties;

    @LoginCheck
    @GetMapping("/auth-url")
    public Result<Map<String, String>> authUrl() {
        String url = steamService.authUrl(UserThreadLocal.getUserId());
        return Result.success(Map.of("url", url));
    }

    @GetMapping("/callback")
    public RedirectView callback(HttpServletRequest request,
                                 @RequestParam(value = "state", required = false) String state) {
        String redirectBase = steamProperties.getFrontendRedirect();
        String separator = redirectBase.contains("?") ? "&" : "?";
        try {
            Map<String, String> params = new HashMap<>();
            request.getParameterMap().forEach((key, values) -> {
                if (values != null && values.length > 0) {
                    params.put(key, values[0]);
                }
            });
            steamService.callback(params, state);
            return new RedirectView(redirectBase + separator + "steam=success");
        } catch (Exception e) {
            return new RedirectView(redirectBase + separator + "steam=fail");
        }
    }

    @LoginCheck
    @GetMapping("/profile")
    public Result<SteamBindVO> profile() {
        return Result.success(steamService.profile(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/users/by-account/{accountId}/profile")
    public Result<SteamBindVO> profileForUserByAccount(@PathVariable("accountId") Long accountId) {
        return Result.success(steamService.profileForViewerByAccount(UserThreadLocal.getUserId(), accountId));
    }

    @LoginCheck
    @GetMapping("/library")
    public Result<List<SteamGameVO>> library() {
        return Result.success(steamService.library(UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @GetMapping("/users/by-account/{accountId}/library")
    public Result<List<SteamGameVO>> libraryForUserByAccount(@PathVariable("accountId") Long accountId) {
        return Result.success(steamService.libraryForViewerByAccount(UserThreadLocal.getUserId(), accountId));
    }

    @LoginCheck
    @GetMapping("/games/{appId}/stats")
    public Result<SteamGameStatsVO> gameStats(@PathVariable("appId") Long appId) {
        return Result.success(steamService.gameStats(UserThreadLocal.getUserId(), appId));
    }

    @LoginCheck
    @PostMapping("/sync")
    public Result<Void> sync() {
        steamService.syncLibrary(UserThreadLocal.getUserId());
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/unbind")
    public Result<Void> unbind() {
        steamService.unbind(UserThreadLocal.getUserId());
        return Result.success(null);
    }
}
