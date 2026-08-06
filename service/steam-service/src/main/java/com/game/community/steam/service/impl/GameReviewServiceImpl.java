package com.game.community.steam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.community.common.exception.BusinessException;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameReviewMapper;
import com.game.community.steam.service.GameReviewService;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.SaveGameReviewDTO;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.entity.game.GameReview;
import com.game.community.model.vo.game.GameRatingStatsVO;
import com.game.community.model.vo.game.GameReviewVO;
import com.game.community.model.vo.user.UserCardInternalVO;
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
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameReviewServiceImpl implements GameReviewService {

    private final GameReviewMapper gameReviewMapper;
    private final GameCatalogMapper gameCatalogMapper;
    private final SteamStoreClient steamStoreClient;
    private final UserFeignClient userFeignClient;
    private final GameSearchIndexProducer gameSearchIndexProducer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveReview(Long appId, Long userId, SaveGameReviewDTO dto) {
        ensureCatalogExists(appId);
        GameReview existing = gameReviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getAppId, appId)
                .eq(GameReview::getUserId, userId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new GameReview();
            existing.setAppId(appId);
            existing.setUserId(userId);
            existing.setStatus(1);
            existing.setCreateTime(now);
        } else if (existing.getStatus() != null && existing.getStatus() == 0) {
            existing.setStatus(1);
        }
        existing.setScore(dto.getScore());
        existing.setContent(StringUtils.hasText(dto.getContent()) ? dto.getContent().trim() : null);
        existing.setUpdateTime(now);
        if (existing.getId() == null) {
            gameReviewMapper.insert(existing);
        } else {
            gameReviewMapper.updateById(existing);
        }
        recomputeCatalogStats(appId);
    }

    @Override
    public PageResult<GameReviewVO> listReviews(Long appId, Long page, Long size) {
        long pageNo = page == null || page < 1 ? 1 : page;
        long pageSize = size == null || size < 1 ? 10 : Math.min(size, 50);
        Page<GameReview> result = gameReviewMapper.selectPage(new Page<>(pageNo, pageSize),
                new LambdaQueryWrapper<GameReview>()
                        .eq(GameReview::getAppId, appId)
                        .eq(GameReview::getStatus, 1)
                        .orderByDesc(GameReview::getCreateTime));
        List<GameReviewVO> records = toReviewVOs(result.getRecords());
        return PageResult.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Override
    public GameReviewVO getMine(Long appId, Long userId) {
        GameReview review = gameReviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getAppId, appId)
                .eq(GameReview::getUserId, userId)
                .eq(GameReview::getStatus, 1)
                .last("LIMIT 1"));
        if (review == null) {
            return null;
        }
        return toReviewVO(review, fetchUserMap(List.of(userId)).get(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMine(Long appId, Long userId) {
        GameReview review = gameReviewMapper.selectOne(new LambdaQueryWrapper<GameReview>()
                .eq(GameReview::getAppId, appId)
                .eq(GameReview::getUserId, userId)
                .eq(GameReview::getStatus, 1)
                .last("LIMIT 1"));
        if (review == null) {
            return;
        }
        review.setStatus(0);
        review.setUpdateTime(LocalDateTime.now());
        gameReviewMapper.updateById(review);
        recomputeCatalogStats(appId);
    }

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
            gameSearchIndexProducer.upsertCatalog(catalog);
        }
    }

    private List<GameReviewVO> toReviewVOs(List<GameReview> reviews) {
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = reviews.stream().map(GameReview::getUserId).filter(Objects::nonNull).distinct().toList();
        Map<Long, UserCardInternalVO> userMap = fetchUserMap(userIds);
        List<GameReviewVO> result = new ArrayList<>();
        for (GameReview review : reviews) {
            result.add(toReviewVO(review, userMap.get(review.getUserId())));
        }
        return result;
    }

    private GameReviewVO toReviewVO(GameReview review, UserCardInternalVO user) {
        GameReviewVO vo = new GameReviewVO();
        vo.setAppId(review.getAppId());
        vo.setAccountId(user == null ? null : user.getAccountId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setAvatar(user.getAvatar());
        }
        vo.setScore(review.getScore());
        vo.setContent(review.getContent());
        vo.setCreateTime(review.getCreateTime());
        vo.setUpdateTime(review.getUpdateTime());
        return vo;
    }

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

    private void ensureCatalogExists(Long appId) {
        if (gameCatalogMapper.selectById(appId) != null) {
            return;
        }
        GameCatalog fetched = steamStoreClient.fetchAppDetails(appId);
        fetched.setCreateTime(LocalDateTime.now());
        fetched.setUpdateTime(LocalDateTime.now());
        gameCatalogMapper.insert(fetched);
    }
}
