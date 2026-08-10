package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.SteamApiConstants;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.common.exception.BusinessException;
import com.game.community.feign.SocialFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.dto.steam.SteamCallbackDTO;
import com.game.community.model.dto.steam.SteamLibrarySyncQuery;
import com.game.community.model.entity.game.UserSteamBind;
import com.game.community.model.entity.game.UserSteamGame;
import com.game.community.model.entity.game.GameAchievement;
import com.game.community.model.entity.game.UserGameAchievement;
import com.game.community.model.vo.game.GameAchievementVO;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.SteamBindVO;
import com.game.community.model.vo.game.SteamGameStatsVO;
import com.game.community.model.vo.game.SteamGameVO;
import com.game.community.model.vo.game.SteamLibrarySyncVO;
import com.game.community.model.vo.game.SteamUserAchievementVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.steam.client.SteamApiClient;
import com.game.community.steam.client.SteamOpenIdService;
import com.game.community.steam.mapper.UserSteamBindMapper;
import com.game.community.steam.mapper.UserSteamGameMapper;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.SteamService;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SteamServiceImpl implements SteamService {

    private final SteamOpenIdService steamOpenIdService;
    private final SteamApiClient steamApiClient;
    private final GameCatalogService gameCatalogService;
    private final SocialFeignClient socialFeignClient;
    private final UserFeignClient userFeignClient;
    private final RedisUtils redisUtils;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final UserSteamBindMapper userSteamBindMapper;
    private final UserSteamGameMapper userSteamGameMapper;
    private final SteamAchievementRefreshService steamAchievementRefreshService;

    @Resource(name = "steamLibrarySyncExecutor")
    private Executor steamLibrarySyncExecutor;

    /** 生成当前登录用户的 Steam OpenID 授权地址，并保存一次性 state。 */
    @Override
    public String authUrl() {
        Long userId = currentUserId();
        String state = UUID.randomUUID().toString().replace("-", "");
        redisUtils.set(SteamRedisConstants.BIND_STATE_KEY_PREFIX + state,
                String.valueOf(userId),
                SteamRedisConstants.BIND_STATE_TTL_SECONDS,
                TimeUnit.SECONDS);
        return steamOpenIdService.buildAuthUrl(state);
    }

    /**
     * 处理 Steam OpenID 回调并完成账号绑定，游戏库由前端随后分页触发同步。
     *
     * <p>state 在后续校验前删除，保证授权请求只能使用一次；Steam API、Redis
     * 和用户服务属于外部 IO，数据库事务不能回滚这些外部操作。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void callback(SteamCallbackDTO request) {
        if (request == null) {
            throw new BusinessException("Steam 回调参数无效");
        }
        String state = request.getState();
        if (!StringUtils.hasText(state)) {
            throw new BusinessException("绑定状态无效");
        }
        String userIdText = redisUtils.get(SteamRedisConstants.BIND_STATE_KEY_PREFIX + state);
        if (!StringUtils.hasText(userIdText)) {
            throw new BusinessException("绑定状态已过期，请重新发起授权");
        }
        Long userId = Long.parseLong(userIdText);
        redisUtils.del(SteamRedisConstants.BIND_STATE_KEY_PREFIX + state);

        String steamId = steamOpenIdService.verifyCallback(request.getOpenIdParams());
        UserSteamBind existing = userSteamBindMapper.selectBySteamId(steamId);
        if (existing != null && !Objects.equals(existing.getUserId(), userId)) {
            throw new BusinessException("该 Steam 账号已绑定其他社区用户");
        }

        SteamApiClient.PlayerSummary summary = steamApiClient.getPlayerSummary(steamId);
        int steamLevel = steamApiClient.getSteamLevel(steamId);

        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        boolean newBind = bind == null;
        LocalDateTime now = LocalDateTime.now();
        if (newBind) {
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
        if (newBind) {
            userSteamBindMapper.insert(bind);
        } else {
            userSteamBindMapper.updateById(bind);
        }

        userFeignClient.updateSteamAccount(userId, steamId);

        // 绑定成功即启动首轮最多五批（100 条）基础库同步，后续由前端按钮继续翻页。
        scheduleInitialLibrarySync(userId);

        log.info("Steam 绑定成功: userId={}, steamId={}", userId, steamId);
    }

    private void scheduleInitialLibrarySync(Long userId) {
        try {
            CompletableFuture.runAsync(() -> {
                try {
                    SteamLibrarySyncQuery query = new SteamLibrarySyncQuery();
                    query.setPage(0);
                    syncLibraryPageByUserId(userId, query);
                } catch (Exception e) {
                    log.warn("绑定后首轮 Steam 游戏库同步失败: userId={}", userId, e);
                }
            }, steamLibrarySyncExecutor);
        } catch (RejectedExecutionException e) {
            log.warn("绑定后 Steam 游戏库同步任务未能入队: userId={}", userId);
        }
    }

    /** 查询当前登录用户绑定的 Steam 资料。 */
    @Override
    public SteamBindVO profile() {
        return profileByUserId(currentUserId());
    }

    /** 查询指定内部用户的 Steam 资料，供查看权限校验后的流程复用。 */
    private SteamBindVO profileByUserId(Long userId) {
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        if (bind == null) {
            return null;
        }
        return toBindVO(bind, resolveAccountId(userId));
    }

    /** 查询目标用户的 Steam 资料，并校验查看者与目标用户的社交权限。 */
    @Override
    public SteamBindVO profileForViewer(Long targetUserId) {
        Long viewerId = currentUserId();
        assertCanViewSteam(viewerId, targetUserId);
        return profileByUserId(targetUserId);
    }

    /** 将对外 accountId 转换为内部 userId 后查询目标用户 Steam 资料。 */
    @Override
    public SteamBindVO profileForViewerByAccount(Long targetAccountId) {
        Long targetUserId = requireUserIdByAccountId(targetAccountId);
        return profileForViewer(targetUserId);
    }

    /** 查询当前登录用户的 Steam 游戏库。 */
    @Override
    public List<SteamGameVO> library() {
        return libraryByUserId(currentUserId());
    }

    /** 查询指定用户当前仍在 Steam 游戏库中的本地数据。 */
    private List<SteamGameVO> libraryByUserId(Long userId) {
        requireBind(userId);
        List<UserSteamGame> games = userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId)
                .eq(UserSteamGame::getIsOwned, 1)
                .orderByDesc(UserSteamGame::getPlaytimeTwoWeeks)
                .orderByDesc(UserSteamGame::getPlaytimeForever)
                .orderByAsc(UserSteamGame::getName));
        List<SteamGameVO> result = new ArrayList<>();
        for (UserSteamGame game : games) {
            SteamGameVO vo = new SteamGameVO();
            vo.setAppId(game.getAppId());
            vo.setName(game.getName());
            vo.setNameZh(game.getNameZh());
            vo.setNameEn(game.getNameEn());
            vo.setIconUrl(game.getIconUrl());
            vo.setCoverUrl(game.getCoverUrl());
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

    /** 查询目标用户公开的 Steam 游戏库，并执行黑名单和公开状态校验。 */
    @Override
    public List<SteamGameVO> libraryForViewer(Long targetUserId) {
        Long viewerId = currentUserId();
        assertCanViewSteam(viewerId, targetUserId);
        UserSteamBind bind = requireBind(targetUserId);
        if (bind.getLibraryPublic() == null || bind.getLibraryPublic() != 1) {
            throw new BusinessException("该用户游戏库未公开");
        }
        return libraryByUserId(targetUserId);
    }

    /** 将对外 accountId 转换为内部 userId 后查询目标用户公开游戏库。 */
    @Override
    public List<SteamGameVO> libraryForViewerByAccount(Long targetAccountId) {
        Long targetUserId = requireUserIdByAccountId(targetAccountId);
        return libraryForViewer(targetUserId);
    }

    /** 分页增量同步当前登录用户的游戏库，每次只处理固定数量的卡片数据。 */
    @Override
    public SteamLibrarySyncVO syncLibrary(SteamLibrarySyncQuery query) {
        return syncLibraryPageByUserId(currentUserId(), query);
    }

    /** 执行本次最多五批的游戏库同步，首次请求从 Steam 获取清单并缓存同步会话。 */
    private SteamLibrarySyncVO syncLibraryPageByUserId(
            Long userId,
            SteamLibrarySyncQuery query) {
        UserSteamBind bind = requireBind(userId);
        String syncId = query == null ? null : query.getSyncId();
        int page = query == null || query.getPage() == null ? 0 : query.getPage();
        if (page < 0) {
            throw new BusinessException("同步页码无效");
        }
        if (!StringUtils.hasText(syncId) && page > 0) {
            throw new BusinessException("缺少游戏库同步会话");
        }

        String lockKey = SteamRedisConstants.SYNC_LIBRARY_LOCK_PREFIX + userId;
        if (Boolean.FALSE.equals(redisUtils.setIfAbsent(
                lockKey, "1", SteamRedisConstants.SYNC_LIBRARY_LOCK_SECONDS))) {
            throw new BusinessException("游戏库正在同步中，请稍后再试");
        }
        try {
            SteamApiClient.OwnedGamesResult ownedGames;
            if (StringUtils.hasText(syncId)) {
                ownedGames = readLibrarySyncData(userId, syncId);
            } else {
                ownedGames = steamApiClient.getOwnedGames(bind.getSteamId());
                if (!ownedGames.isLibraryPublic()) {
                    updatePrivateLibrary(bind);
                    return buildSyncResult(null, page, page, 0, 0, 0, false, true, false);
                }
                syncId = UUID.randomUUID().toString().replace("-", "");
                cacheLibrarySyncData(userId, syncId, ownedGames);
            }

            List<SteamApiClient.OwnedGame> ownedList = ownedGames.getGames() == null
                    ? List.of()
                    : ownedGames.getGames();
            int total = ownedGames.getGameCount() > 0
                    ? ownedGames.getGameCount() : ownedList.size();
            int fromIndex = page * SteamApiConstants.LIBRARY_SYNC_BATCH_SIZE;
            if (fromIndex > ownedList.size()) {
                throw new BusinessException("同步页码超出游戏库范围");
            }
            ensurePreviousLibraryBatchesReady(userId, syncId, fromIndex);
            int nextPage = page;
            int batchCount = 0;
            int processedCount = fromIndex;
            while (nextPage * SteamApiConstants.LIBRARY_SYNC_BATCH_SIZE < ownedList.size()
                    && batchCount < SteamApiConstants.LIBRARY_SYNC_MAX_BATCHES_PER_REQUEST) {
                int batchStart = nextPage * SteamApiConstants.LIBRARY_SYNC_BATCH_SIZE;
                int batchEnd = Math.min(
                        batchStart + SteamApiConstants.LIBRARY_SYNC_BATCH_SIZE,
                        ownedList.size());
                List<SteamApiClient.OwnedGame> batch = ownedList.subList(batchStart, batchEnd);
                syncLibraryBatch(userId, syncId, batch);
                processedCount = batchEnd;
                nextPage++;
                batchCount++;
            }

            boolean completed = processedCount >= ownedList.size();
            if (completed) {
                completeLibrarySync(userId, syncId, bind, total);
                scheduleAchievementEnrichment(
                        userId,
                        bind.getSteamId(),
                        ownedList.subList(0, Math.min(
                                SteamApiConstants.LIBRARY_SYNC_BATCH_SIZE, ownedList.size())));
                clearLibrarySyncData(userId, syncId);
            }
            return buildSyncResult(
                    syncId,
                    nextPage == page ? page : nextPage - 1,
                    nextPage,
                    total,
                    batchCount,
                    processedCount,
                    true,
                    completed,
                    !completed);
        } finally {
            redisUtils.del(lockKey);
        }
    }

    /** 查询当前同步会话缓存，避免每一页重复请求 Steam 游戏清单。 */
    private SteamApiClient.OwnedGamesResult readLibrarySyncData(Long userId, String syncId) {
        String json = redisUtils.get(librarySyncDataKey(userId, syncId));
        if (!StringUtils.hasText(json)) {
            throw new BusinessException("游戏库同步会话已过期，请重新同步");
        }
        try {
            return objectMapper.readValue(json, SteamApiClient.OwnedGamesResult.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException("游戏库同步会话无效");
        }
    }

    /** 缓存本次同步的轻量游戏清单，缓存内容不包含游戏详情。 */
    private void cacheLibrarySyncData(
            Long userId,
            String syncId,
            SteamApiClient.OwnedGamesResult ownedGames) {
        try {
            redisUtils.set(
                    librarySyncDataKey(userId, syncId),
                    objectMapper.writeValueAsString(ownedGames),
                    SteamRedisConstants.LIBRARY_SYNC_DATA_TTL_SECONDS,
                    TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new BusinessException("保存游戏库同步会话失败");
        }
    }

    /** 处理一批 Steam 游戏数据；成就和公共游戏信息在后续异步补充。 */
    private void syncLibraryBatch(
            Long userId,
            String syncId,
            List<SteamApiClient.OwnedGame> batch) {
        transactionTemplate.executeWithoutResult(status -> upsertLibraryBatch(
                userId, syncId, batch));
        scheduleBasicInfoEnrichment(batch);
    }

    /** 将当前批次缺少公共基础信息的游戏提交到有界线程池。 */
    private void scheduleBasicInfoEnrichment(List<SteamApiClient.OwnedGame> batch) {
        List<Long> appIds = batch.stream()
                .map(SteamApiClient.OwnedGame::getAppId)
                .toList();
        List<Long> missingIds = gameCatalogService.findMissingBasicInfoIds(appIds);
        for (Long appId : missingIds) {
            CompletableFuture.runAsync(
                    () -> gameCatalogService.warmupBasicInfo(appId),
                    steamLibrarySyncExecutor);
        }
    }

    /** 首次完整同步后优先补充第一页游戏的成就汇总。 */
    private void scheduleAchievementEnrichment(
            Long userId,
            String steamId,
            List<SteamApiClient.OwnedGame> firstPage) {
        for (SteamApiClient.OwnedGame game : firstPage) {
            if (game.getPlaytimeForever() <= 0 && game.getPlaytimeTwoWeeks() <= 0) {
                continue;
            }
            CompletableFuture.runAsync(
                    () -> refreshAchievementSummary(userId, steamId, game.getAppId()),
                    steamLibrarySyncExecutor);
        }
    }

    /** 读取单个游戏成就汇总并原子更新用户游戏记录。 */
    private void refreshAchievementSummary(Long userId, String steamId, long appId) {
        try {
            SteamApiClient.AchievementProgress progress =
                    steamApiClient.getPlayerAchievements(steamId, appId);
            if (progress == null) {
                return;
            }
            transactionTemplate.executeWithoutResult(status -> {
                UserSteamGame game = userSteamGameMapper.selectOne(new LambdaQueryWrapper<UserSteamGame>()
                        .eq(UserSteamGame::getUserId, userId)
                        .eq(UserSteamGame::getAppId, appId)
                        .eq(UserSteamGame::getIsOwned, 1));
                if (game == null) {
                    return;
                }
                game.setAchievementUnlocked(progress.getUnlocked());
                game.setAchievementTotal(progress.getTotal());
                userSteamGameMapper.updateById(game);
            });
        } catch (Exception e) {
            log.warn("补充游戏成就汇总失败: userId={}, appId={}", userId, appId, e);
        }
    }

    /** 校验当前页之前的批次已经成功更新，避免跳页漏写数据。 */
    private void ensurePreviousLibraryBatchesReady(
            Long userId,
            String syncId,
            int fromIndex) {
        if (fromIndex == 0) {
            return;
        }
        long stagedCount = userSteamGameMapper.selectCount(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId)
                .eq(UserSteamGame::getSyncId, syncId));
        if (stagedCount < fromIndex) {
            throw new BusinessException("前序游戏库批次尚未完成，请按顺序重试");
        }
    }

    /** 在一个数据库事务内对当前用户的一批游戏执行新增或更新。 */
    private void upsertLibraryBatch(
            Long userId,
            String syncId,
            List<SteamApiClient.OwnedGame> batch) {
        if (batch.isEmpty()) {
            return;
        }
        List<Long> appIds = batch.stream().map(SteamApiClient.OwnedGame::getAppId).toList();
        Map<Long, UserSteamGame> existingMap = new HashMap<>();
        userSteamGameMapper.selectList(new LambdaQueryWrapper<UserSteamGame>()
                        .eq(UserSteamGame::getUserId, userId)
                        .in(UserSteamGame::getAppId, appIds))
                .forEach(game -> existingMap.put(game.getAppId(), game));

        LocalDateTime now = LocalDateTime.now();
        for (SteamApiClient.OwnedGame game : batch) {
            UserSteamGame entity = existingMap.get(game.getAppId());
            boolean newGame = entity == null;
            if (newGame) {
                entity = new UserSteamGame();
                entity.setUserId(userId);
                entity.setAppId(game.getAppId());
                entity.setAchievementUnlocked(0);
                entity.setAchievementTotal(0);
            }
            entity.setNameZh(firstText(game.getNameZh(), entity.getNameZh()));
            entity.setNameEn(firstText(game.getNameEn(), entity.getNameEn()));
            entity.setName(resolveDisplayName(
                    entity.getNameZh(), entity.getNameEn(), entity.getName()));
            entity.setIconUrl(game.getIconUrl());
            entity.setCoverUrl(game.getCoverUrl());
            entity.setPlaytimeForever(game.getPlaytimeForever());
            entity.setPlaytimeTwoWeeks(game.getPlaytimeTwoWeeks());
            entity.setLastPlayedAt(toLastPlayedAt(game.getLastPlayedEpoch()));
            entity.setSyncedAt(now);
            entity.setSyncId(syncId);
            entity.setIsOwned(1);
            if (newGame) {
                userSteamGameMapper.insert(entity);
            } else {
                userSteamGameMapper.updateById(entity);
            }
        }
    }

    /** 完成全部批次后，标记本次清单中已经不存在的旧游戏。 */
    private void completeLibrarySync(
            Long userId,
            String syncId,
            UserSteamBind bind,
            int total) {
        transactionTemplate.executeWithoutResult(status -> {
            userSteamGameMapper.markNotInSync(userId, syncId);
            bind.setGameCount(total);
            bind.setLibraryPublic(1);
            bind.setLibrarySyncedAt(LocalDateTime.now());
            bind.setUpdateTime(LocalDateTime.now());
            userSteamBindMapper.updateById(bind);
        });
    }

    /** 更新 Steam 游戏库不可公开状态，保留已有游戏数据。 */
    private void updatePrivateLibrary(UserSteamBind bind) {
        bind.setLibraryPublic(0);
        bind.setLibrarySyncedAt(LocalDateTime.now());
        bind.setUpdateTime(LocalDateTime.now());
        userSteamBindMapper.updateById(bind);
    }

    /** 删除已经完成的 Redis 同步会话。 */
    private void clearLibrarySyncData(Long userId, String syncId) {
        redisUtils.del(librarySyncDataKey(userId, syncId));
    }

    /** 生成游戏库同步会话 Redis Key。 */
    private String librarySyncDataKey(Long userId, String syncId) {
        return SteamRedisConstants.LIBRARY_SYNC_DATA_KEY_PREFIX + userId + ":" + syncId;
    }

    /** 组装本次分批同步结果。 */
    private SteamLibrarySyncVO buildSyncResult(
            String syncId,
            int page,
            int nextPage,
            int total,
            int batchCount,
            int processedCount,
            boolean libraryPublic,
            boolean completed,
            boolean hasMore) {
        SteamLibrarySyncVO result = new SteamLibrarySyncVO();
        result.setSyncId(syncId);
        result.setPage(page);
        result.setNextPage(nextPage);
        result.setPageSize(SteamApiConstants.LIBRARY_SYNC_BATCH_SIZE);
        result.setTotal(total);
        result.setBatchCount(batchCount);
        result.setProcessedCount(processedCount);
        result.setLibraryPublic(libraryPublic);
        result.setCompleted(completed);
        result.setHasMore(hasMore);
        return result;
    }

    /** 按中文、英文、旧展示名称顺序选择卡片名称。 */
    private String resolveDisplayName(String nameZh, String nameEn, String fallback) {
        return firstText(nameZh, nameEn, fallback);
    }

    /** 返回第一个非空文本。 */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /** 查询当前登录用户指定游戏的拥有状态、游玩时长和成就明细。 */
    @Override
    public SteamGameStatsVO gameStats(Long appId) {
        Long userId = currentUserId();
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
                .eq(UserSteamGame::getAppId, appId)
                .eq(UserSteamGame::getIsOwned, 1));
        if (game == null) {
            return stats;
        }
        stats.setOwned(true);
        stats.setName(game.getName());
        stats.setNameZh(game.getNameZh());
        stats.setNameEn(game.getNameEn());
        stats.setPlaytimeForever(game.getPlaytimeForever());
        stats.setPlaytimeTwoWeeks(game.getPlaytimeTwoWeeks());
        stats.setLastPlayedAt(game.getLastPlayedAt());
        stats.setAchievementUnlocked(game.getAchievementUnlocked());
        stats.setAchievementTotal(game.getAchievementTotal());
        List<GameAchievement> definitions = steamAchievementRefreshService.listDefinitions(appId);
        List<UserGameAchievement> userAchievements = steamAchievementRefreshService.listCurrent(userId, appId);
        Map<String, UserGameAchievement> userMap = new HashMap<>();
        for (UserGameAchievement item : userAchievements) {
            userMap.put(item.getApiName(), item);
        }
        List<SteamUserAchievementVO> achievements = mergeCachedAchievements(definitions, userMap);
        stats.setAchievements(achievements);
        stats.setAchievementStatus(game.getAchievementRefreshStatus());
        stats.setAchievementSyncedAt(game.getAchievementSyncedAt());
        stats.setAchievementNextRefreshAt(game.getAchievementNextRefreshAt());
        if (steamAchievementRefreshService.userStale(game)) {
            steamAchievementRefreshService.queueUserRefresh(userId, bind, game, false);
        }
        if (stats.getAchievementTotal() == null && !achievements.isEmpty()) {
            stats.setAchievementTotal(achievements.size());
        }
        return stats;
    }

    /** 手动刷新只绕过 8 小时阈值，不绕过 Redis 锁；旧快照在任务成功前继续展示。 */
    @Override
    public SteamGameStatsVO syncAchievements(Long appId) {
        Long userId = currentUserId();
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        UserSteamGame game = userSteamGameMapper.selectOne(new LambdaQueryWrapper<UserSteamGame>()
                .eq(UserSteamGame::getUserId, userId)
                .eq(UserSteamGame::getAppId, appId)
                .eq(UserSteamGame::getIsOwned, 1));
        if (bind == null || game == null) {
            return gameStats(appId);
        }
        steamAchievementRefreshService.queueUserRefresh(userId, bind, game, true);
        return gameStats(appId);
    }

    private List<SteamUserAchievementVO> mergeCachedAchievements(
            List<GameAchievement> definitions,
            Map<String, UserGameAchievement> userMap) {
        List<SteamUserAchievementVO> result = new ArrayList<>();
        for (GameAchievement definition : definitions) {
            UserGameAchievement user = userMap.get(definition.getApiName());
            SteamUserAchievementVO vo = new SteamUserAchievementVO();
            vo.setApiName(definition.getApiName());
            vo.setName(definition.getName());
            vo.setDescription(definition.getDescription());
            vo.setIconUrl(normalizeAchievementIconUrl(definition.getIconUrl()));
            vo.setGlobalPercent(definition.getGlobalPercent());
            if (user != null) {
                vo.setUnlocked(user.getUnlocked() != null && user.getUnlocked() == 1);
                vo.setUnlockTime(user.getUnlockTime());
            }
            result.add(vo);
        }
        result.sort(Comparator
                .comparing((SteamUserAchievementVO item) -> Boolean.TRUE.equals(item.getUnlocked()))
                .reversed()
                .thenComparing(SteamUserAchievementVO::getName, Comparator.nullsLast(String::compareToIgnoreCase)));
        return result;
    }

    /** 将旧版 Steam 成就 CDN 地址转换为早期游戏库使用的图标 CDN。 */
    private String normalizeAchievementIconUrl(String iconUrl) {
        if (!StringUtils.hasText(iconUrl)) {
            return iconUrl;
        }
        return iconUrl.replace("steamcdn-a.akamaihd.net", "media.steampowered.com")
                .replace("cdn.akamai.steamstatic.com", "media.steampowered.com");
    }

    /** 优先读取游戏百科成就定义，缺失时回退到 Steam 官方 Schema。 */
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

    /** 合并成就定义、用户解锁状态和全球解锁比例，并按展示规则排序。 */
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

    /** 将实时获取的成就汇总回写到用户游戏快照。 */
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

    /** 将 Steam 的 Unix 秒级时间戳转换为本地时间。 */
    private LocalDateTime toLastPlayedAt(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    /** 将成就解锁 Unix 秒级时间戳转换为本地时间。 */
    private LocalDateTime toUnlockTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault());
    }

    /** 解绑当前登录用户的 Steam 账号，并删除本地绑定及游戏库快照。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind() {
        Long userId = currentUserId();
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        if (bind == null) {
            return;
        }
        userSteamGameMapper.deleteByUserId(userId);
        userSteamBindMapper.deleteById(userId);

        userFeignClient.updateSteamAccount(userId, "");
        log.info("Steam 解绑成功: userId={}", userId);
    }

    /** 查询用户 Steam 绑定，不存在时抛出统一业务异常。 */
    private UserSteamBind requireBind(Long userId) {
        UserSteamBind bind = userSteamBindMapper.selectById(userId);
        if (bind == null) {
            throw new BusinessException("尚未绑定 Steam 账号");
        }
        return bind;
    }

    /** 校验查看者是否可以查看目标用户的 Steam 内容。 */
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

    /** 将 Steam 绑定实体转换为对外响应对象。 */
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

    /** 将内部 userId 转换为对外展示的 accountId。 */
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

    /** 将对外 accountId 转换为内部 userId，不存在时抛出业务异常。 */
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

    /** 从网关写入的用户上下文获取当前登录用户。 */
    private Long currentUserId() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }
}
