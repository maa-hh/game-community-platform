package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.model.entity.game.GameAchievement;
import com.game.community.model.entity.game.UserGameAchievement;
import com.game.community.model.entity.game.UserSteamGame;
import com.game.community.model.entity.game.UserSteamBind;
import com.game.community.steam.client.SteamApiClient;
import com.game.community.steam.mapper.GameAchievementMapper;
import com.game.community.steam.mapper.UserGameAchievementMapper;
import com.game.community.steam.mapper.UserSteamGameMapper;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Steam 成就缓存协调器。
 *
 * <p>请求线程只负责读取旧快照和投递任务。任务只有在完整拉取成功后才切换
 * 用户快照的 current 版本，因此中途失败不会把新旧两批数据混在一起。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SteamAchievementRefreshService {

    private static final long PUBLIC_TTL_HOURS = 24;
    private static final long USER_TTL_HOURS = 8;

    private final SteamApiClient steamApiClient;
    private final RedisUtils redisUtils;
    private final GameAchievementMapper gameAchievementMapper;
    private final UserGameAchievementMapper userGameAchievementMapper;
    private final UserSteamGameMapper userSteamGameMapper;
    private final TransactionTemplate transactionTemplate;

    public List<GameAchievement> listDefinitions(Long appId) {
        return gameAchievementMapper.selectList(new LambdaQueryWrapper<GameAchievement>()
                .eq(GameAchievement::getAppId, appId)
                .orderByAsc(GameAchievement::getId));
    }

    public List<UserGameAchievement> listCurrent(Long userId, Long appId) {
        return userGameAchievementMapper.selectList(new LambdaQueryWrapper<UserGameAchievement>()
                .eq(UserGameAchievement::getUserId, userId)
                .eq(UserGameAchievement::getAppId, appId)
                .eq(UserGameAchievement::getIsCurrent, 1)
                .orderByAsc(UserGameAchievement::getId));
    }

    public boolean publicStale(Long appId) {
        return listDefinitions(appId).stream().findFirst()
                .map(item -> item.getSyncedAt() == null
                        || item.getSyncedAt().isBefore(LocalDateTime.now().minusHours(PUBLIC_TTL_HOURS)))
                .orElse(true);
    }

    public boolean userStale(UserSteamGame game) {
        return game == null
                || game.getAchievementSyncedAt() == null
                || game.getAchievementSyncedAt().isBefore(LocalDateTime.now().minusHours(USER_TTL_HOURS));
    }

    /** 取得分布式锁后再投递，避免同一游戏被高并发请求重复拉取。 */
    public boolean queueUserRefresh(Long userId, UserSteamBind bind, UserSteamGame game, boolean force) {
        if (userId == null || bind == null || game == null) {
            return false;
        }
        if (!force && !userStale(game)) {
            return false;
        }
        String lockKey = SteamRedisConstants.USER_ACHIEVEMENT_REFRESH_LOCK_PREFIX
                + userId + ":" + game.getAppId();
        String token = UUID.randomUUID().toString();
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                lockKey, token, SteamRedisConstants.USER_ACHIEVEMENT_REFRESH_LOCK_SECONDS))) {
            return false;
        }
        markAttempt(game, "LOADING");
        try {
            refreshUserAsync(userId, bind.getSteamId(), game.getAppId(), lockKey, token);
            return true;
        } catch (RejectedExecutionException e) {
            redisUtils.unlock(lockKey, token);
            markFailure(game, "FAILED");
            log.warn("Steam 用户成就刷新线程池已满: userId={}, appId={}", userId, game.getAppId());
            return false;
        }
    }

    @Async("steamAchievementRefreshExecutor")
    public void refreshUserAsync(Long userId, String steamId, Long appId, String lockKey, String token) {
        try {
            List<SteamApiClient.PlayerAchievement> player =
                    steamApiClient.getPlayerAchievementDetails(steamId, appId);
            List<SteamApiClient.AchievementDefinition> definitions = steamApiClient.getAchievementSchema(appId);
            if (definitions.isEmpty() && player.isEmpty()) {
                updateUserState(userId, appId, "NOT_AVAILABLE", false);
                return;
            }
            if (definitions.isEmpty()) {
                definitions = player.stream().map(item -> {
                    SteamApiClient.AchievementDefinition definition =
                            new SteamApiClient.AchievementDefinition();
                    definition.setApiName(item.getApiName());
                    definition.setName(item.getApiName());
                    return definition;
                }).toList();
            }
            Map<String, SteamApiClient.PlayerAchievement> playerMap = new HashMap<>();
            for (SteamApiClient.PlayerAchievement item : player) {
                playerMap.put(item.getApiName(), item);
            }
            Map<String, Double> global = steamApiClient.getGlobalAchievementPercentages(appId);
            persistPublicDefinitions(appId, definitions, global);
            persistUserSnapshot(userId, appId, definitions, playerMap);
        } catch (Exception e) {
            log.warn("Steam 用户成就刷新失败: userId={}, appId={}", userId, appId, e);
            updateUserState(userId, appId, "FAILED", false);
        } finally {
            redisUtils.unlock(lockKey, token);
        }
    }

    private void persistPublicDefinitions(Long appId,
                                          List<SteamApiClient.AchievementDefinition> definitions,
                                          Map<String, Double> global) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            for (SteamApiClient.AchievementDefinition definition : definitions) {
                GameAchievement entity = gameAchievementMapper.selectOne(new LambdaQueryWrapper<GameAchievement>()
                        .eq(GameAchievement::getAppId, appId)
                        .eq(GameAchievement::getApiName, definition.getApiName()));
                if (entity == null) {
                    entity = new GameAchievement();
                    entity.setAppId(appId);
                    entity.setApiName(definition.getApiName());
                    entity.setCreateTime(now);
                    gameAchievementMapper.insert(entity);
                }
                entity.setName(definition.getName());
                entity.setDescription(definition.getDescription());
                entity.setIconUrl(definition.getIconUrl());
                entity.setGlobalPercent(global.get(definition.getApiName()));
                entity.setSyncedAt(now);
                entity.setUpdateTime(now);
                gameAchievementMapper.updateById(entity);
            }
        });
    }

    private void persistUserSnapshot(Long userId, Long appId,
                                     List<SteamApiClient.AchievementDefinition> definitions,
                                     Map<String, SteamApiClient.PlayerAchievement> playerMap) {
        String syncId = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            userGameAchievementMapper.clearCurrent(userId, appId);
            for (SteamApiClient.AchievementDefinition definition : definitions) {
                SteamApiClient.PlayerAchievement player = playerMap.get(definition.getApiName());
                UserGameAchievement entity = new UserGameAchievement();
                entity.setUserId(userId);
                entity.setAppId(appId);
                entity.setApiName(definition.getApiName());
                entity.setUnlocked(player != null && player.isUnlocked() ? 1 : 0);
                entity.setUnlockTime(player == null ? null : toTime(player.getUnlockEpoch()));
                entity.setSyncId(syncId);
                entity.setIsCurrent(1);
                entity.setSyncedAt(now);
                entity.setCreateTime(now);
                entity.setUpdateTime(now);
                userGameAchievementMapper.insert(entity);
            }
            UserSteamGame game = userSteamGameMapper.selectOne(new LambdaQueryWrapper<UserSteamGame>()
                    .eq(UserSteamGame::getUserId, userId)
                    .eq(UserSteamGame::getAppId, appId));
            if (game != null) {
                game.setAchievementUnlocked((int) playerMap.values().stream()
                        .filter(SteamApiClient.PlayerAchievement::isUnlocked).count());
                game.setAchievementTotal(definitions.size());
                game.setAchievementSyncedAt(now);
                game.setAchievementNextRefreshAt(now.plusHours(USER_TTL_HOURS));
                game.setAchievementRefreshStatus("READY");
                game.setAchievementSyncId(syncId);
                game.setAchievementFailCount(0);
                userSteamGameMapper.updateById(game);
            }
        });
    }

    private void markAttempt(UserSteamGame game, String state) {
        game.setAchievementLastAttemptAt(LocalDateTime.now());
        game.setAchievementRefreshStatus(state);
        game.setAchievementNextRefreshAt(LocalDateTime.now().plusHours(USER_TTL_HOURS));
        userSteamGameMapper.updateById(game);
    }

    private void markFailure(UserSteamGame game, String state) {
        game.setAchievementLastAttemptAt(LocalDateTime.now());
        game.setAchievementRefreshStatus(state);
        game.setAchievementFailCount((game.getAchievementFailCount() == null ? 0 : game.getAchievementFailCount()) + 1);
        userSteamGameMapper.updateById(game);
    }

    private void updateUserState(Long userId, Long appId, String state, boolean success) {
        UserSteamGame game = userSteamGameMapper.selectOne(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId).eq(UserSteamGame::getAppId, appId));
        if (game == null) {
            return;
        }
        if (success) {
            game.setAchievementSyncedAt(LocalDateTime.now());
            game.setAchievementNextRefreshAt(LocalDateTime.now().plusHours(USER_TTL_HOURS));
        } else {
            game.setAchievementFailCount((game.getAchievementFailCount() == null ? 0 : game.getAchievementFailCount()) + 1);
        }
        game.setAchievementRefreshStatus(state);
        game.setAchievementLastAttemptAt(LocalDateTime.now());
        userSteamGameMapper.updateById(game);
    }

    private LocalDateTime toTime(long epochSeconds) {
        return epochSeconds <= 0 ? null : LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(epochSeconds), java.time.ZoneId.systemDefault());
    }
}
