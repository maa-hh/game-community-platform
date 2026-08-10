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
import com.game.community.utils.ThreadLocal.UserThreadLocal;
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

    /** 查询当前用户关注的游戏，并合并 Steam 快照和游戏目录信息。 */
    @Override
    public List<UserGameFollowVO> listFollows() {
        Long userId = currentUserId();
        List<UserGameFollow> follows = userGameFollowMapper.selectList(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .orderByDesc(UserGameFollow::getCreateTime));
        if (follows.isEmpty()) {
            return List.of();
        }
        Map<Long, UserSteamGame> steamGameMap = userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                        .eq(UserSteamGame::getUserId, userId)
                        .eq(UserSteamGame::getIsOwned, 1))
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
                if (StringUtils.hasText(steamGame.getCoverUrl())) {
                    vo.setCoverUrl(steamGame.getCoverUrl());
                }
            }
            fillCatalogInfo(vo, catalogMap.get(follow.getAppId()));
            result.add(vo);
        }
        return result;
    }

    /** 查询当前用户是否关注指定游戏。 */
    @Override
    public boolean isFollowed(Long appId) {
        Long userId = currentUserId();
        if (appId == null) {
            return false;
        }
        return userGameFollowMapper.selectCount(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .eq(UserGameFollow::getAppId, appId)) > 0;
    }

    /** 批量查询当前用户的游戏关注状态。 */
    @Override
    public Map<Long, Boolean> checkFollowBatch(List<Long> appIds) {
        Long userId = currentUserId();
        if (appIds == null || appIds.isEmpty()) {
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

    /** 保存当前用户对游戏的关注关系，并确保游戏目录已经存在。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void follow(FollowGameDTO dto) {
        Long userId = currentUserId();
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

    /** 删除当前用户对指定游戏的关注关系。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfollow(Long appId) {
        Long userId = currentUserId();
        userGameFollowMapper.delete(new LambdaQueryWrapper<UserGameFollow>()
                .eq(UserGameFollow::getUserId, userId)
                .eq(UserGameFollow::getAppId, appId));
    }

    /** 将当前用户 Steam 游戏库中尚未关注的游戏批量导入。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int importFromSteam() {
        Long userId = currentUserId();
        List<UserSteamGame> steamGames = userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId)
                .eq(UserSteamGame::getIsOwned, 1));
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

    /** 补充游戏目录展示字段，目录缺失时保留 Steam 快照信息。 */
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

    /** 确保关注目标存在于游戏目录中。 */
    private void ensureCatalog(Long appId) {
        GameDetailVO detail = gameCatalogService.getDetail(appId);
        if (detail == null) {
            throw new BusinessException("游戏不存在或暂时无法获取");
        }
    }

    /** 将关注来源规范化为允许的来源值。 */
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

    /** 从网关写入的用户上下文获取当前登录用户。 */
    private Long currentUserId() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }

}
