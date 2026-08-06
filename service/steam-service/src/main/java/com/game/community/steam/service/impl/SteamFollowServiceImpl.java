package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.model.dto.game.FollowGameDTO;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.entity.game.UserGameFollow;
import com.game.community.model.entity.game.UserSteamGame;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.UserGameFollowVO;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.UserGameFollowMapper;
import com.game.community.steam.mapper.UserSteamGameMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.SteamFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamFollowServiceImpl implements SteamFollowService {

    private final UserGameFollowMapper userGameFollowMapper;
    private final UserSteamGameMapper userSteamGameMapper;
    private final GameCatalogService gameCatalogService;
    private final GameCatalogMapper gameCatalogMapper;

    @Override
    public List<UserGameFollowVO> listFollows(Long userId) {
        List<UserGameFollow> follows = userGameFollowMapper.selectList(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .orderByDesc(UserGameFollow::getCreateTime));
        if (follows.isEmpty()) {
            return List.of();
        }
        Map<Long, UserSteamGame> steamGameMap = userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                        .eq(UserSteamGame::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(UserSteamGame::getAppId, Function.identity(), (a, b) -> a));
        List<Long> appIds = follows.stream().map(UserGameFollow::getAppId).filter(id -> id != null).distinct().toList();
        Map<Long, GameCatalog> catalogMap = gameCatalogMapper.selectBatchIds(appIds).stream()
                .collect(Collectors.toMap(GameCatalog::getAppId, Function.identity(), (a, b) -> a));
        List<UserGameFollowVO> result = new ArrayList<>();
        for (UserGameFollow follow : follows) {
            UserGameFollowVO vo = new UserGameFollowVO();
            vo.setAppId(follow.getAppId());
            vo.setSource(follow.getSource());
            vo.setFollowTime(follow.getCreateTime());
            UserSteamGame steamGame = steamGameMap.get(follow.getAppId());
            vo.setSteamOwned(steamGame != null);
            if (steamGame != null) {
                vo.setPlaytimeForever(steamGame.getPlaytimeForever());
                if (StringUtils.hasText(steamGame.getName())) {
                    vo.setName(steamGame.getName());
                }
            }
            fillCatalogInfo(vo, catalogMap.get(follow.getAppId()));
            result.add(vo);
        }
        return result;
    }

    @Override
    public boolean isFollowed(Long userId, Long appId) {
        if (userId == null || appId == null) {
            return false;
        }
        return userGameFollowMapper.selectCount(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .eq(UserGameFollow::getAppId, appId)) > 0;
    }

    @Override
    public Map<Long, Boolean> checkFollowBatch(Long userId, List<Long> appIds) {
        if (userId == null || appIds == null || appIds.isEmpty()) {
            return Map.of();
        }
        List<Long> distinctAppIds = appIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctAppIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> followedAppIds = userGameFollowMapper.selectList(new LambdaQueryWrapper<UserGameFollow>()
                        .eq(UserGameFollow::getUserId, userId)
                        .in(UserGameFollow::getAppId, distinctAppIds)
                        .select(UserGameFollow::getAppId))
                .stream()
                .map(UserGameFollow::getAppId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, Boolean> result = new HashMap<>(distinctAppIds.size());
        for (Long appId : distinctAppIds) {
            result.put(appId, followedAppIds.contains(appId));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(Long userId, FollowGameDTO dto) {
        if (dto == null || dto.getAppId() == null) {
            throw new BusinessException("游戏 ID 无效");
        }
        Long appId = dto.getAppId();
        ensureCatalog(appId);
        UserGameFollow existing = userGameFollowMapper.selectOne(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .eq(UserGameFollow::getAppId, appId)
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        UserGameFollow follow = new UserGameFollow();
        follow.setUserId(userId);
        follow.setAppId(appId);
        follow.setSource(resolveSource(dto.getSource()));
        follow.setCreateTime(LocalDateTime.now());
        userGameFollowMapper.insert(follow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long userId, Long appId) {
        userGameFollowMapper.delete(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .eq(UserGameFollow::getAppId, appId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importFromSteam(Long userId) {
        List<UserSteamGame> steamGames = userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId));
        if (steamGames.isEmpty()) {
            return 0;
        }
        List<Long> steamAppIds = steamGames.stream()
                .map(UserSteamGame::getAppId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Set<Long> existingAppIds = steamAppIds.isEmpty()
                ? Set.of()
                : userGameFollowMapper.selectList(new LambdaQueryWrapper<UserGameFollow>()
                                .eq(UserGameFollow::getUserId, userId)
                                .in(UserGameFollow::getAppId, steamAppIds)
                                .select(UserGameFollow::getAppId))
                        .stream()
                        .map(UserGameFollow::getAppId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(HashSet::new));
        int imported = 0;
        for (UserSteamGame steamGame : steamGames) {
            if (steamGame.getAppId() == null || existingAppIds.contains(steamGame.getAppId())) {
                continue;
            }
            try {
                ensureCatalog(steamGame.getAppId());
            } catch (Exception e) {
                log.warn("导入 Steam 游戏失败: userId={}, appId={}", userId, steamGame.getAppId(), e);
                continue;
            }
            UserGameFollow follow = new UserGameFollow();
            follow.setUserId(userId);
            follow.setAppId(steamGame.getAppId());
            follow.setSource(GameCatalogConstants.FOLLOW_SOURCE_STEAM_IMPORT);
            follow.setCreateTime(LocalDateTime.now());
            userGameFollowMapper.insert(follow);
            existingAppIds.add(steamGame.getAppId());
            imported++;
        }
        return imported;
    }

    private void fillCatalogInfo(UserGameFollowVO vo, GameCatalog catalog) {
        if (catalog == null) {
            if (!StringUtils.hasText(vo.getName())) {
                vo.setName("游戏 " + vo.getAppId());
            }
            return;
        }
        if (!StringUtils.hasText(vo.getName())) {
            vo.setName(StringUtils.hasText(catalog.getDisplayName())
                    ? catalog.getDisplayName() : catalog.getSteamName());
        }
        String cover = StringUtils.hasText(catalog.getCoverOverride())
                ? catalog.getCoverOverride() : catalog.getHeaderImage();
        if (StringUtils.hasText(cover)) {
            vo.setCoverUrl(cover);
        }
        if (catalog.getAvgScore() != null) {
            vo.setAvgScore(catalog.getAvgScore());
        }
        if (catalog.getReviewCount() != null) {
            vo.setReviewCount(catalog.getReviewCount());
        }
        if (catalog.getDiscussCount() != null) {
            vo.setDiscussCount(catalog.getDiscussCount());
        }
    }

    private void ensureCatalog(Long appId) {
        GameDetailVO detail = gameCatalogService.getDetail(appId);
        if (detail == null) {
            throw new BusinessException("游戏不存在或暂时无法获取");
        }
    }

    private String resolveSource(String source) {
        if (!StringUtils.hasText(source)) {
            return GameCatalogConstants.FOLLOW_SOURCE_MANUAL;
        }
        return switch (source) {
            case GameCatalogConstants.FOLLOW_SOURCE_DISCOVER,
                    GameCatalogConstants.FOLLOW_SOURCE_STEAM_IMPORT,
                    GameCatalogConstants.FOLLOW_SOURCE_MANUAL -> source;
            default -> GameCatalogConstants.FOLLOW_SOURCE_MANUAL;
        };
    }
}
