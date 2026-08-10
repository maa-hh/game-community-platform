package com.game.community.steam.service;

import com.game.community.model.dto.steam.SteamCallbackDTO;
import com.game.community.model.dto.steam.SteamLibrarySyncQuery;
import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.game.SteamGameStatsVO;
import com.game.community.model.vo.game.SteamGameVO;
import com.game.community.model.vo.game.SteamLibrarySyncVO;

import java.util.List;

public interface SteamService {

    /** 生成当前登录用户的 Steam OpenID 授权地址。 */
    String authUrl();

    /** 处理 Steam OpenID 回调并完成账号绑定。 */
    void callback(SteamCallbackDTO request);

    /** 查询当前登录用户绑定的 Steam 资料。 */
    SteamBindVO profile();

    /** 查询目标用户的 Steam 资料并校验查看权限。 */
    SteamBindVO profileForViewer(Long targetUserId);

    /** 按对外 accountId 查询目标用户的 Steam 资料。 */
    SteamBindVO profileForViewerByAccount(Long targetAccountId);

    /** 查询当前登录用户的 Steam 游戏库。 */
    List<SteamGameVO> library();

    /** 查询目标用户公开的 Steam 游戏库。 */
    List<SteamGameVO> libraryForViewer(Long targetUserId);

    /** 按对外 accountId 查询目标用户公开的 Steam 游戏库。 */
    List<SteamGameVO> libraryForViewerByAccount(Long targetAccountId);

    /** 分页增量同步当前登录用户的 Steam 游戏库。 */
    SteamLibrarySyncVO syncLibrary(SteamLibrarySyncQuery query);

    /** 查询当前登录用户指定游戏的游玩和成就信息。 */
    SteamGameStatsVO gameStats(Long appId);

    /** 强制投递一次玩家成就同步任务，并返回当前缓存。 */
    SteamGameStatsVO syncAchievements(Long appId);

    /** 解绑当前登录用户的 Steam 账号。 */
    void unbind();
}
