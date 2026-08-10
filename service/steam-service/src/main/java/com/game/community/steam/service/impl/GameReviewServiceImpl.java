package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameReviewMapper;
import com.game.community.steam.service.GameReviewService;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.feign.UserFeignClient;
import com.game.community.feign.SocialFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.SaveGameReviewDTO;
import com.game.community.model.dto.game.GameReviewPageQuery;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.entity.game.GameReview;
import com.game.community.model.enums.game.GameReviewStatus;
import com.game.community.model.vo.game.GameRatingStatsVO;
import com.game.community.model.vo.game.GameReviewVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import com.game.community.model.vo.social.GameReviewSocialStatsVO;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameReviewServiceImpl implements GameReviewService {

    private final GameReviewMapper gameReviewMapper;
    private final GameCatalogMapper gameCatalogMapper;
    private final GameCatalogService gameCatalogService;
    private final UserFeignClient userFeignClient;
    private final GameSearchIndexProducer gameSearchIndexProducer;
    private final SocialFeignClient socialFeignClient;
    private final RedisUtils redisUtils;

    /** 保存或恢复当前用户对游戏的短评，并刷新游戏评分汇总。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReview(Long appId, SaveGameReviewDTO dto) {
        Long userId = currentUserId();
        ensureCatalogExists(appId);
        GameReview existing = gameReviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getAppId, appId)
                .eq(GameReview::getUserId, userId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new GameReview();
            existing.setReviewId(newPublicReviewId());
            existing.setAppId(appId);
            existing.setUserId(userId);
            existing.setStatus(GameReviewStatus.ACTIVE.getCode());
            existing.setCreateTime(now);
        } else if (existing.getStatus() != null
                && existing.getStatus() == GameReviewStatus.DELETED.getCode()) {
            existing.setStatus(GameReviewStatus.ACTIVE.getCode());
        }
        existing.setScore(dto.getScore());
        existing.setContent(StringUtils.hasText(dto.getContent()) ? dto.getContent().trim() : null);
        existing.setUpdateTime(now);
        if (existing.getId() == null) {
            gameReviewMapper.insert(existing);
        } else {
            gameReviewMapper.updateById(existing);
        }
        syncReviewToSocial(existing);
        recomputeCatalogStats(appId);
    }

    /** 分页查询游戏的有效短评，并批量补充用户展示资料。 */
    @Override
    public PageResult<GameReviewVO> listReviews(GameReviewPageQuery query) {
        GameReviewPageQuery resolved = query == null ? new GameReviewPageQuery() : query;
        long pageNo = resolved.getPage() == null || resolved.getPage() < 1
                ? 1 : resolved.getPage();
        long pageSize = resolved.getSize() == null || resolved.getSize() < 1
                ? 10 : Math.min(resolved.getSize(), 50);
        Page<GameReview> result;
        if ("hot".equalsIgnoreCase(resolved.getSort())) {
            result = listBySocialHot(resolved.getAppId(), pageNo, pageSize);
        } else {
            result = gameReviewMapper.selectPage(new Page<>(pageNo, pageSize),
                    new LambdaQueryWrapper<GameReview>()
                            .eq(GameReview::getAppId, resolved.getAppId())
                            .eq(GameReview::getStatus, GameReviewStatus.ACTIVE.getCode())
                            .orderByDesc(GameReview::getCreateTime)
                            .orderByDesc(GameReview::getId));
        }
        result.getRecords().forEach(this::ensurePublicReviewId);
        result.getRecords().forEach(this::syncReviewToSocial);
        List<GameReviewVO> records = toReviewVOs(result.getRecords());
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /** 查询当前用户对游戏的有效短评。 */
    @Override
    public GameReviewVO getMine(Long appId) {
        Long userId = currentUserId();
        GameReview review = gameReviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getAppId, appId)
                .eq(GameReview::getUserId, userId)
                .eq(GameReview::getStatus, GameReviewStatus.ACTIVE.getCode())
                .last("LIMIT 1"));
        if (review == null) {
            return null;
        }
        ensurePublicReviewId(review);
        syncReviewToSocial(review);
        return toReviewVO(review, fetchUserMap(List.of(userId)).get(userId),
                socialStats(List.of(review)).getOrDefault(review.getReviewId(), emptyStats(review.getReviewId())));
    }

    /** 软删除当前用户的短评，并重新计算游戏评分汇总。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMine(Long appId) {
        Long userId = currentUserId();
        GameReview review = gameReviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getAppId, appId)
                .eq(GameReview::getUserId, userId)
                .eq(GameReview::getStatus, GameReviewStatus.ACTIVE.getCode())
                .last("LIMIT 1"));
        if (review == null) {
            return;
        }
        review.setStatus(GameReviewStatus.DELETED.getCode());
        review.setUpdateTime(LocalDateTime.now());
        gameReviewMapper.updateById(review);
        syncReviewRemovalToSocial(review);
        recomputeCatalogStats(appId);
    }

    /** 查询游戏评分汇总，优先使用游戏目录中的已计算数据。 */
    @Override
    public GameRatingStatsVO getRatingStats(Long appId) {
        GameCatalog catalog = gameCatalogMapper.selectById(appId);
        GameRatingStatsVO vo = new GameRatingStatsVO();
        if (catalog != null && catalog.getReviewCount() != null) {
            vo.setReviewCount(catalog.getReviewCount());
            vo.setAvgScore(catalog.getAvgScore());
            return vo;
        }
        vo.setReviewCount(gameReviewMapper.selectReviewCount(appId));
        BigDecimal avg = gameReviewMapper.selectAvgScore(appId);
        if (avg != null) {
            vo.setAvgScore(avg.setScale(1, RoundingMode.HALF_UP));
        }
        return vo;
    }

    /** 根据有效短评重新计算游戏平均分、数量并刷新搜索索引。 */
    private void recomputeCatalogStats(Long appId) {
        Integer count = gameReviewMapper.selectReviewCount(appId);
        BigDecimal avg = gameReviewMapper.selectAvgScore(appId);
        GameCatalog catalog = gameCatalogMapper.selectById(appId);
        if (catalog == null) {
            ensureCatalogExists(appId);
            catalog = gameCatalogMapper.selectById(appId);
        }
        if (catalog != null) {
            catalog.setReviewCount(count == null ? 0 : count);
            catalog.setAvgScore(avg == null ? null : avg.setScale(1, RoundingMode.HALF_UP));
            catalog.setUpdateTime(LocalDateTime.now());
            gameCatalogMapper.updateById(catalog);
            // 详情缓存中也包含本站评分；评分保存/删除后必须失效，否则详情卡片会继续展示旧值。
            redisUtils.del(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId);
            // 目录更新后由 Kafka 异步刷新 ES，避免搜索结果继续使用旧评分。
            gameSearchIndexProducer.upsertCatalog(catalog);
        }
    }

    /** 批量将评论实体转换为响应对象，避免逐条请求用户服务。 */
    private List<GameReviewVO> toReviewVOs(List<GameReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = reviews.stream().map(GameReview::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, UserCardInternalVO> userMap = fetchUserMap(userIds);
        Map<String, GameReviewSocialStatsVO> statsMap = socialStats(reviews);
        List<GameReviewVO> result = new ArrayList<>();
        for (GameReview review : reviews) {
            result.add(toReviewVO(review, userMap.get(review.getUserId()),
                    statsMap.getOrDefault(review.getReviewId(), emptyStats(review.getReviewId()))));
        }
        return result;
    }

    /** 将单条评论和用户资料转换为对外响应对象。 */
    private GameReviewVO toReviewVO(GameReview review, UserCardInternalVO user,
                                    GameReviewSocialStatsVO stats) {
        GameReviewVO vo = new GameReviewVO();
        vo.setReviewId(review.getReviewId());
        vo.setAppId(review.getAppId());
        vo.setAccountId(user == null ? null : user.getAccountId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }
        vo.setScore(review.getScore());
        vo.setContent(StringUtils.hasText(stats == null ? null : stats.getContent())
                ? stats.getContent() : review.getContent());
        vo.setCreateTime(review.getCreateTime());
        vo.setUpdateTime(review.getUpdateTime());
        vo.setLikeCount(stats == null ? 0L : stats.getLikeCount());
        vo.setReplyCount(stats == null ? 0L : stats.getReplyCount());
        vo.setLiked(stats != null && Boolean.TRUE.equals(stats.getLiked()));
        return vo;
    }

    private Page<GameReview> listBySocialHot(Long appId, long pageNo, long pageSize) {
        try {
            Long viewerId = UserThreadLocal.getUserId();
            var ranked = socialFeignClient.rankGameReviews(appId, pageNo, pageSize, viewerId);
            if (ranked != null && ranked.getData() != null && !ranked.getData().isEmpty()) {
                List<String> ids = ranked.getData().stream().map(GameReviewSocialStatsVO::getReviewId).toList();
                List<GameReview> reviews = gameReviewMapper.selectList(new LambdaQueryWrapper<GameReview>()
                        .in(GameReview::getReviewId, ids)
                        .eq(GameReview::getStatus, GameReviewStatus.ACTIVE.getCode()));
                Map<String, GameReview> reviewMap = reviews.stream()
                        .collect(Collectors.toMap(GameReview::getReviewId, item -> item, (a, b) -> a));
                Page<GameReview> page = new Page<>(pageNo, pageSize);
                page.setRecords(ids.stream().map(reviewMap::get).filter(Objects::nonNull).toList());
                page.setTotal(ranked.getTotal() == null ? page.getRecords().size() : ranked.getTotal());
                return page;
            }
        } catch (Exception e) {
            log.warn("读取游戏短评点赞排序失败，回退最近发布: appId={}", appId, e);
        }
        return gameReviewMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<GameReview>().eq(GameReview::getAppId, appId)
                        .eq(GameReview::getStatus, GameReviewStatus.ACTIVE.getCode())
                        .orderByDesc(GameReview::getCreateTime));
    }

    private Map<String, GameReviewSocialStatsVO> socialStats(List<GameReview> reviews) {
        if (reviews == null || reviews.isEmpty()) return Map.of();
        try {
            List<String> ids = reviews.stream().map(GameReview::getReviewId).filter(StringUtils::hasText).toList();
            var result = socialFeignClient.getGameReviewStats(ids, UserThreadLocal.getUserId());
            if (result == null || result.getData() == null) return Map.of();
            return result.getData().stream().collect(Collectors.toMap(GameReviewSocialStatsVO::getReviewId,
                    item -> item, (a, b) -> a));
        } catch (Exception e) {
            log.debug("读取短评社交统计失败", e);
            return Map.of();
        }
    }

    private GameReviewSocialStatsVO emptyStats(String reviewId) {
        GameReviewSocialStatsVO stats = new GameReviewSocialStatsVO();
        stats.setReviewId(reviewId);
        stats.setLikeCount(0L);
        stats.setReplyCount(0L);
        stats.setLiked(false);
        return stats;
    }

    private void syncReviewToSocial(GameReview review) {
        if (review == null || !StringUtils.hasText(review.getReviewId())) return;
        try {
            socialFeignClient.ensureGameReview(review.getReviewId(), review.getAppId(), review.getUserId(),
                    review.getContent(), review.getCreateTime() == null ? null : review.getCreateTime().toString());
        } catch (Exception e) {
            log.debug("同步短评社交元数据失败: reviewId={}", review.getReviewId(), e);
        }
    }

    private void syncReviewRemovalToSocial(GameReview review) {
        if (review == null || !StringUtils.hasText(review.getReviewId())) return;
        try {
            socialFeignClient.removeGameReview(review.getReviewId());
        } catch (Exception e) {
            log.debug("同步删除短评社交元数据失败: reviewId={}", review.getReviewId(), e);
        }
    }

    /** 批量查询评论作者资料，用户服务失败时返回空映射。 */
    private Map<Long, UserCardInternalVO> fetchUserMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        try {
            var result = userFeignClient.getUsersByUserIds(userIds);
            if (result == null || result.getData() == null) {
                return Map.of();
            }
            return result.getData().stream()
                    .filter(card -> card.getUserId() != null)
                    .collect(Collectors.toMap(UserCardInternalVO::getUserId, card -> card, (a, b) -> a));
        } catch (Exception e) {
            log.warn("批量获取用户名片失败", e);
            return new HashMap<>();
        }
    }

    /** 评论写入前确保游戏目录存在。 */
    private void ensureCatalogExists(Long appId) {
        if (gameCatalogMapper.selectById(appId) != null) {
            return;
        }
        gameCatalogService.getDetail(appId);
    }

    /** 兼容迁移前的历史短评，首次读到时补齐公开标识。 */
    private void ensurePublicReviewId(GameReview review) {
        if (review == null || StringUtils.hasText(review.getReviewId()) || review.getId() == null) {
            return;
        }
        review.setReviewId(newPublicReviewId());
        gameReviewMapper.updateById(review);
    }

    private String newPublicReviewId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /** 获取当前登录用户，保证用户上下文不由 Controller 传入。 */
    private Long currentUserId() {
        Long userId = UserThreadLocal.getUserId();
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        return userId;
    }
}
