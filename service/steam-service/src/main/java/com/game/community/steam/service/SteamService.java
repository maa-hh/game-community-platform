package com.game.community.steam.service;

import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.game.SteamGameStatsVO;
import com.game.community.model.vo.game.SteamGameVO;

import java.util.List;
import java.util.Map;

public interface SteamService {

    String authUrl(Long userId);

    void callback(Map<String, String> params, String state);

    SteamBindVO profile(Long userId);

    SteamBindVO profileForViewer(Long viewerId, Long targetUserId);

    SteamBindVO profileForViewerByAccount(Long viewerId, Long targetAccountId);

    List<SteamGameVO> library(Long userId);

    List<SteamGameVO> libraryForViewer(Long viewerId, Long targetUserId);

    List<SteamGameVO> libraryForViewerByAccount(Long viewerId, Long targetAccountId);

    void syncLibrary(Long userId);

    SteamGameStatsVO gameStats(Long userId, long appId);

    void unbind(Long userId);
}
