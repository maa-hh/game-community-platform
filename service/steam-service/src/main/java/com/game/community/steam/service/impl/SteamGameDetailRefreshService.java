package com.game.community.steam.service.impl;

import com.game.community.model.entity.game.GameCatalog;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.service.SteamGameDetailService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamGameDetailRefreshService {

    private final SteamStoreClient steamStoreClient;
    private final SteamGameDetailService steamGameDetailService;
    private final RedisUtils redisUtils;

    @Async("steamDetailRefreshExecutor")
    public void refresh(Long appId) {
        if (appId == null) {
            return;
        }
        String lockKey = SteamRedisConstants.DETAIL_REFRESH_LOCK_PREFIX + appId;
        String lockToken = UUID.randomUUID().toString();
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                lockKey, lockToken, SteamRedisConstants.DETAIL_REFRESH_LOCK_SECONDS))) {
            return;
        }
        try {
            GameCatalog catalog = steamStoreClient.fetchAppDetails(appId);
            steamGameDetailService.saveFromCatalog(catalog);
            redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
        } catch (Exception e) {
            log.warn("Steam 游戏富详情懒更新失败: appId={}", appId, e);
        } finally {
            redisUtils.unlock(lockKey, lockToken);
        }
    }
}
