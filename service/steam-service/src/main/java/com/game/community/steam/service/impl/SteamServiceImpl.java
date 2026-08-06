package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.entity.game.UserSteamBind;
import com.game.community.model.entity.game.UserSteamGame;
import com.game.community.model.vo.game.GameAchievementVO;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.game.SteamGameStatsVO;
import com.game.community.model.vo.game.SteamGameVO;
import com.game.community.model.vo.game.SteamUserAchievementVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.steam.client.SteamApiClient;
import com.game.community.steam.client.SteamOpenIdService;
import com.game.community.steam.config.SteamProperties;
import com.game.community.steam.mapper.UserSteamBindMapper;
import com.game.community.steam.mapper.UserSteamGameMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.SteamService;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamServiceImpl implements SteamService {

    private final SteamProperties steamProperties;
    private final SteamOpenIdService steamOpenIdService;
    private final SteamApiClient steamApiClient;
    private final GameCatalogService gameCatalogService;
    private final SocialFeignClient socialFeignClient;
    private final UserFeignClient userFeignClient;
    private final RedisUtils redisUtils;
    private final UserSteamBindMapper userSteamBindMapper;
    private final UserSteamGameMapper userSteamGameMapper;

    @Override
    public String authUrl(Long userId) {
        String state = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(SteamRedisConstants.BIND_STATE_KEY_PREFIX + state,
                String.valueOf(userId),
                SteamRedisConstants.BIND_STATE_TTL_SECONDS,
                TimeUnit.SECONDS);
        return steamOpenIdService.buildAuthUrl(state);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void callback(Map<String, String> params, String state) {
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("绑定状态无效");
        }
        String userIdText = redisUtils.get(SteamRedisConstants.BIND_STATE_KEY_PREFIX + state);
        if (!StringUtils.hasText(userIdText)) {
            throw new BusinessException("绑定状态已过期，请重新发起授权");
        }
        Long userId = Long.parseLong(userIdText);
        redisUtils.del(SteamRedisConstants.BIND_STATE_KEY_PREFIX + state);

        String steamId = steamOpenIdService.verifyCallback(params);
        UserSteamBind existing = userSteamBindMapper.selectBySteamId(steamId);
        if (existing != null && !Objects.equals(existing.getUserId(), userId)) {
            throw new BusinessException("该 Steam 账号已绑定其他社区用户");
        }

        SteamApiClient.PlayerSummary summary = steamApiClient.getPlayerSummary(steamId);
        int steamLevel = steamApiClient.getSteamLevel(steamId);

        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        LocalDateTime now = LocalDateTime.now();
        if (bind == null) {
            bind = new UserSteamBind();
            bind.setUserId(userId);
            bind.setCreateTime(now);
        }
        bind.setSteamId(steamId);
        bind.setPersonaName(summary.getPersonaName());
        bind.setAvatarUrl(summary.getAvatarUrl());
        bind.setProfileUrl(summary.getProfileUrl());
        bind.setSteamLevel(steamLevel);
        bind.setUpdateTime(now);
        if (bind.getGameCount() == null) {
            bind.setGameCount(0);
        }
        if (bind.getLibraryPublic() == null) {
            bind.setLibraryPublic(0);
        }
        if (userSteamBindMapper.selectById(userId) == null) {
            userSteamBindMapper.insert(bind);
        } else {
            userSteamBindMapper.updateById(bind);
        }

        userFeignClient.updateSteamAccount(userId, steamId);

        syncLibrary(userId);
        log.info("Steam 绑定成功: userId={}, steamId={}", userId, steamId);
    }

    @Override
    public SteamBindVO profile(Long userId) {
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        if (bind == null) {
            return null;
        }
        return toBindVO(bind, resolveAccountId(userId));
    }

    @Override
    public SteamBindVO profileForViewer(Long viewerId, Long targetUserId) {
        assertCanViewSteam(viewerId, targetUserId);
        return profile(targetUserId);
    }

    @Override
    public SteamBindVO profileForViewerByAccount(Long viewerId, Long targetAccountId) {
        Long targetUserId = requireUserIdByAccountId(targetAccountId);
        return profileForViewer(viewerId, targetUserId);
    }

    @Override
    public List<SteamGameVO> library(Long userId) {
        requireBind(userId);
        List<UserSteamGame> games = userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId)
                .orderByDesc(UserSteamGame::getPlaytimeTwoWeeks)
                .orderByDesc(UserSteamGame::getPlaytimeForever)
                .orderByAsc(UserSteamGame::getName));
        List<SteamGameVO> result = new ArrayList<>();
        for (UserSteamGame game : games) {
            SteamGameVO vo = new SteamGameVO();
            vo.setAppId(game.getAppId());
            vo.setName(game.getName());
            vo.setIconUrl(game.getIconUrl());
            vo.setPlaytimeForever(game.getPlaytimeForever());
            vo.setPlaytimeTwoWeeks(game.getPlaytimeTwoWeeks());
            vo.setLastPlayedAt(game.getLastPlayedAt());
            vo.setAchievementUnlocked(game.getAchievementUnlocked());
            vo.setAchievementTotal(game.getAchievementTotal());
            vo.setSyncedAt(game.getSyncedAt());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<SteamGameVO> libraryForViewer(Long viewerId, Long targetUserId) {
        assertCanViewSteam(viewerId, targetUserId);
        UserSteamBind bind = requireBind(targetUserId);
        if (bind.getLibraryPublic() == null || bind.getLibraryPublic() != 1) {
            throw new BusinessException("该用户游戏库未公开");
        }
        return library(targetUserId);
    }

    @Override
    public List<SteamGameVO> libraryForViewerByAccount(Long viewerId, Long targetAccountId) {
        Long targetUserId = requireUserIdByAccountId(targetAccountId);
        return libraryForViewer(viewerId, targetUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncLibrary(Long userId) {
        String lockKey = SteamRedisConstants.SYNC_LIBRARY_LOCK_PREFIX + userId;
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(lockKey, "1", SteamRedisConstants.SYNC_LIBRARY_LOCK_SECONDS))) {
            throw new BusinessException("游戏库正在同步中，请稍后再试");
        }
        try {
            doSyncLibrary(userId);
        } finally {
            redisUtils.del(lockKey);
        }
    }

    private void doSyncLibrary(Long userId) {
        UserSteamBind bind = requireBind(userId);
        SteamApiClient.OwnedGamesResult ownedGames = steamApiClient.getOwnedGames(bind.getSteamId());
        LocalDateTime now = LocalDateTime.now();

        // Steam 请求失败或游戏库未公开时不能清空已有快照，避免瞬时网络故障造成数据丢失。
        if (!ownedGames.isLibraryPublic()) {
            bind.setLibraryPublic(0);
            bind.setLibrarySyncedAt(now);
            bind.setUpdateTime(now);
            userSteamBindMapper.updateById(bind);
            return;
        }

        userSteamGameMapper.deleteByUserId(userId);
        List<SteamApiClient.OwnedGame> ownedList = ownedGames.getGames() == null
                ? List.of()
                : new ArrayList<>(ownedGames.getGames());
        if (!ownedList.isEmpty()) {
            for (SteamApiClient.OwnedGame game : ownedList) {
                UserSteamGame entity = new UserSteamGame();
                entity.setUserId(userId);
                entity.setAppId(game.getAppId());
                entity.setName(game.getName());
                entity.setIconUrl(game.getIconUrl());
                entity.setPlaytimeForever(game.getPlaytimeForever());
                entity.setPlaytimeTwoWeeks(game.getPlaytimeTwoWeeks());
                entity.setLastPlayedAt(toLastPlayedAt(game.getLastPlayedEpoch()));
                entity.setSyncedAt(now);
                userSteamGameMapper.insert(entity);
            }
            syncAchievementsForTopGames(bind.getSteamId(), userId, ownedList);
        }

        bind.setGameCount(ownedGames.getGameCount());
        bind.setLibraryPublic(ownedGames.isLibraryPublic() ? 1 : 0);
        bind.setLibrarySyncedAt(now);
        bind.setUpdateTime(now);
        userSteamBindMapper.updateById(bind);
    }

    private void syncAchievementsForTopGames(
            String steamId,
            Long userId,
            List<SteamApiClient.OwnedGame> ownedList) {
        ownedList.stream()
                .sorted(Comparator
                        .comparingInt((SteamApiClient.OwnedGame game) -> Math.max(
                                game.getPlaytimeForever(),
                                game.getPlaytimeTwoWeeks()))
                        .reversed())
                .limit(SteamApiConstants.ACHIEVEMENT_SYNC_LIMIT)
                .forEach(game -> {
                    if (game.getPlaytimeForever() <= 0 && game.getPlaytimeTwoWeeks() <= 0) {
                        return;
                    }
                    SteamApiClient.AchievementProgress progress =
                            steamApiClient.getPlayerAchievements(steamId, game.getAppId());
                    if (progress == null) {
                        return;
                    }
                    UserSteamGame update = new UserSteamGame();
                    update.setAchievementUnlocked(progress.getUnlocked());
                    update.setAchievementTotal(progress.getTotal());
                    userSteamGameMapper.update(
                            update,
                            new LambdaQueryWrapper<UserSteamGame>()
                                    .eq(UserSteamGame::getUserId, userId)
                                    .eq(UserSteamGame::getAppId, game.getAppId()));
                });
    }

    @Override
    public SteamGameStatsVO gameStats(Long userId, long appId) {
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        SteamGameStatsVO stats = new SteamGameStatsVO();
        stats.setAppId(appId);
        stats.setOwned(false);
        stats.setAchievements(List.of());
        if (bind == null) {
            return stats;
        }
        UserSteamGame game = userSteamGameMapper.selectOne(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId)
                .eq(UserSteamGame::getAppId, appId));
        if (game == null) {
            return stats;
        }
        stats.setOwned(true);
        stats.setName(game.getName());
        stats.setPlaytimeForever(game.getPlaytimeForever());
        stats.setPlaytimeTwoWeeks(game.getPlaytimeTwoWeeks());
        stats.setLastPlayedAt(game.getLastPlayedAt());
        stats.setAchievementUnlocked(game.getAchievementUnlocked());
        stats.setAchievementTotal(game.getAchievementTotal());

        List<SteamApiClient.AchievementDefinition> definitions = loadAchievementDefinitions(appId);
        List<SteamApiClient.PlayerAchievement> playerAchievements =
                steamApiClient.getPlayerAchievementDetails(bind.getSteamId(), appId);
        Map<String, Double> globalPercents = steamApiClient.getGlobalAchievementPercentages(appId);
        Map<String, SteamApiClient.PlayerAchievement> playerMap = new HashMap<>();
        for (SteamApiClient.PlayerAchievement item : playerAchievements) {
            playerMap.put(item.getApiName(), item);
        }

        if (!playerAchievements.isEmpty()) {
            int unlocked = 0;
            for (SteamApiClient.PlayerAchievement item : playerAchievements) {
                if (item.isUnlocked()) {
                    unlocked++;
                }
            }
            stats.setAchievementUnlocked(unlocked);
            stats.setAchievementTotal(playerAchievements.size());
            persistAchievementSummary(userId, appId, unlocked, playerAchievements.size());
        }

        List<SteamUserAchievementVO> achievements = mergeAchievements(
                definitions,
                playerMap,
                globalPercents);
        stats.setAchievements(achievements);
        if (stats.getAchievementTotal() == null && !achievements.isEmpty()) {
            stats.setAchievementTotal(achievements.size());
        }
        return stats;
    }

    private List<SteamApiClient.AchievementDefinition> loadAchievementDefinitions(long appId) {
        try {
            GameDetailVO detail = gameCatalogService.getDetail(appId);
            if (detail != null
                    && detail.getAchievementHighlights() != null
                    && !detail.getAchievementHighlights().isEmpty()) {
                List<SteamApiClient.AchievementDefinition> definitions = new ArrayList<>();
                for (GameAchievementVO item : detail.getAchievementHighlights()) {
                    if (!StringUtils.hasText(item.getApiName()) && !StringUtils.hasText(item.getName())) {
                        continue;
                    }
                    if (!StringUtils.hasText(item.getApiName())) {
                        return steamApiClient.getAchievementSchema(appId);
                    }
                    SteamApiClient.AchievementDefinition definition =
                            new SteamApiClient.AchievementDefinition();
                    definition.setApiName(StringUtils.hasText(item.getApiName())
                            ? item.getApiName()
                            : item.getName());
                    definition.setName(item.getName());
                    definition.setDescription(item.getDescription());
                    definition.setIconUrl(item.getIconUrl());
                    definitions.add(definition);
                }
                if (!definitions.isEmpty()) {
                    return definitions;
                }
            }
        } catch (Exception e) {
            log.debug("读取游戏百科成就失败: appId={}", appId, e);
        }
        return steamApiClient.getAchievementSchema(appId);
    }

    private List<SteamUserAchievementVO> mergeAchievements(
            List<SteamApiClient.AchievementDefinition> definitions,
            Map<String, SteamApiClient.PlayerAchievement> playerMap,
            Map<String, Double> globalPercents) {
        List<SteamUserAchievementVO> achievements = new ArrayList<>();
        if (!definitions.isEmpty()) {
            for (SteamApiClient.AchievementDefinition definition : definitions) {
                SteamUserAchievementVO vo = new SteamUserAchievementVO();
                vo.setApiName(definition.getApiName());
                vo.setName(definition.getName());
                vo.setDescription(definition.getDescription());
                vo.setIconUrl(definition.getIconUrl());
                SteamApiClient.PlayerAchievement player = playerMap.get(definition.getApiName());
                if (player != null) {
                    vo.setUnlocked(player.isUnlocked());
                    vo.setUnlockTime(toUnlockTime(player.getUnlockEpoch()));
                } else {
                    vo.setUnlocked(false);
                }
                vo.setGlobalPercent(globalPercents.get(definition.getApiName()));
                achievements.add(vo);
            }
        } else {
            for (SteamApiClient.PlayerAchievement player : playerMap.values()) {
                SteamUserAchievementVO vo = new SteamUserAchievementVO();
                vo.setApiName(player.getApiName());
                vo.setName(player.getApiName());
                vo.setUnlocked(player.isUnlocked());
                vo.setUnlockTime(toUnlockTime(player.getUnlockEpoch()));
                vo.setGlobalPercent(globalPercents.get(player.getApiName()));
                achievements.add(vo);
            }
        }
        achievements.sort(Comparator
                .comparing((SteamUserAchievementVO item) -> Boolean.TRUE.equals(item.getUnlocked()))
                .reversed()
                .thenComparing(
                        (SteamUserAchievementVO item) -> item.getUnlockTime() == null
                                ? LocalDateTime.MIN
                                : item.getUnlockTime(),
                        Comparator.reverseOrder())
                .thenComparing(
                        (SteamUserAchievementVO item) -> item.getGlobalPercent() == null
                                ? 0D
                                : item.getGlobalPercent(),
                        Comparator.reverseOrder())
                .thenComparing(
                        SteamUserAchievementVO::getName,
                        Comparator.nullsLast(String::compareToIgnoreCase)));
        return achievements;
    }

    private void persistAchievementSummary(
            Long userId,
            long appId,
            int unlocked,
            int total) {
        UserSteamGame update = new UserSteamGame();
        update.setAchievementUnlocked(unlocked);
        update.setAchievementTotal(total);
        userSteamGameMapper.update(
                update,
                new LambdaQueryWrapper<UserSteamGame>()
                        .eq(UserSteamGame::getUserId, userId)
                        .eq(UserSteamGame::getAppId, appId));
    }

    private LocalDateTime toLastPlayedAt(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    private LocalDateTime toUnlockTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(Long userId) {
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        if (bind == null) {
            return;
        }
        userSteamGameMapper.deleteByUserId(userId);
        userSteamBindMapper.deleteById(userId);

        userFeignClient.updateSteamAccount(userId, "");
        log.info("Steam 解绑成功: userId={}", userId);
    }

    private UserSteamBind requireBind(Long userId) {
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        if (bind == null) {
            throw new BusinessException("尚未绑定 Steam 账号");
        }
        return bind;
    }

    private void assertCanViewSteam(Long viewerId, Long targetUserId) {
        if (viewerId == null || targetUserId == null) {
            throw new BusinessException("用户不存在");
        }
        if (Objects.equals(viewerId, targetUserId)) {
            return;
        }
        Result<Boolean> result = socialFeignClient.hasBlackRelation(viewerId, targetUserId);
        if (result != null && Boolean.TRUE.equals(result.getData())) {
            throw new BusinessException("无法查看该用户内容");
        }
    }

    private SteamBindVO toBindVO(UserSteamBind bind, Long accountId) {
        SteamBindVO vo = new SteamBindVO();
        vo.setAccountId(accountId);
        vo.setSteamId(bind.getSteamId());
        vo.setPersonaName(bind.getPersonaName());
        vo.setAvatarUrl(bind.getAvatarUrl());
        vo.setProfileUrl(bind.getProfileUrl());
        vo.setSteamLevel(bind.getSteamLevel());
        vo.setGameCount(bind.getGameCount());
        vo.setLibraryPublic(bind.getLibraryPublic() != null && bind.getLibraryPublic() == 1);
        vo.setLibrarySyncedAt(bind.getLibrarySyncedAt());
        vo.setBindTime(bind.getCreateTime());
        return vo;
    }

    private Long resolveAccountId(Long userId) {
        if (userId == null) {
            return null;
        }
        try {
            Result<List<UserCardInternalVO>> result = userFeignClient.getUsersByUserIds(List.of(userId));
            if (result == null || result.getData() == null || result.getData().isEmpty()) {
                return null;
            }
            return result.getData().get(0).getAccountId();
        } catch (Exception e) {
            log.warn("获取用户 accountId 失败: userId={}", userId, e);
            return null;
        }
    }

    private Long requireUserIdByAccountId(Long accountId) {
        if (accountId == null) {
            throw new BusinessException("用户不存在");
        }
        try {
            Result<UserCardInternalVO> result = userFeignClient.getUserByAccountId(accountId);
            if (result == null || result.getData() == null || result.getData().getUserId() == null) {
                throw new BusinessException("用户不存在");
            }
            return result.getData().getUserId();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("按 accountId 解析用户失败: accountId={}", accountId, e);
            throw new BusinessException("用户不存在");
        }
    }
}
