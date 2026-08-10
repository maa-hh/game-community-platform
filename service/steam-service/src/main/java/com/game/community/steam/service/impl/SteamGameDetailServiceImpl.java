package com.game.community.steam.service.impl;

import com.game.community.model.mongo.SteamGameAchievement;
import com.game.community.model.mongo.SteamGameDetail;
import com.game.community.model.mongo.SteamGameMovie;
import com.game.community.model.mongo.SteamGameScreenshot;
import com.game.community.model.payload.steam.SteamAchievementDefinitionPayload;
import com.game.community.model.payload.steam.SteamGameDetailsPayload;
import com.game.community.model.payload.steam.SteamMoviePayload;
import com.game.community.model.payload.steam.SteamScreenshotPayload;
import com.game.community.common.constant.steam.GameCatalogConstants;
import com.game.community.steam.mongo.SteamGameDetailRepository;
import com.game.community.steam.service.SteamGameDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SteamGameDetailServiceImpl implements SteamGameDetailService {

    private final SteamGameDetailRepository repository;

    /** 查询 Mongo 中保存的游戏富详情。 */
    @Override
    public SteamGameDetail find(Long appId) {
        return appId == null ? null : repository.findByAppId(appId).orElse(null);
    }

    /** 判断游戏富详情是否缺失或已经超过七天刷新阈值。 */
    @Override
    public boolean needsRefresh(SteamGameDetail detail) {
        return detail == null
                || isIncomplete(detail)
                || detail.getRichSyncedAt() == null
                || detail.getRichSyncedAt().isBefore(LocalDateTime.now()
                .minusDays(GameCatalogConstants.STALE_DAYS));
    }

    /** 将 Steam 富详情 payload 映射为 Mongo 文档并按 appId 幂等保存。 */
    @Override
    public void save(SteamGameDetailsPayload payload) {
        if (payload == null || payload.getAppId() == null) {
            return;
        }
        SteamGameDetail detail = repository.findByAppId(payload.getAppId()).orElseGet(SteamGameDetail::new);
        detail.setAppId(payload.getAppId());
        if (StringUtils.hasText(payload.getShortDescription()) || detail.getSteamShortDesc() == null) {
            detail.setSteamShortDesc(payload.getShortDescription());
        }
        if (StringUtils.hasText(payload.getAboutHtml()) || detail.getSteamAboutHtml() == null) {
            detail.setSteamAboutHtml(payload.getAboutHtml());
        }
        if (payload.getScreenshots() != null && !payload.getScreenshots().isEmpty()
                || detail.getScreenshots() == null) {
            detail.setScreenshots(toScreenshots(payload.getScreenshots()));
        }
        if (payload.getMovies() != null && !payload.getMovies().isEmpty()
                || detail.getMovies() == null) {
            detail.setMovies(toMovies(payload.getMovies()));
        }
        if (payload.getCategories() != null && !payload.getCategories().isEmpty()
                || detail.getCategories() == null) {
            detail.setCategories(payload.getCategories());
        }
        if (payload.getMetacriticScore() != null) {
            detail.setMetacriticScore(payload.getMetacriticScore());
        }
        if (StringUtils.hasText(payload.getMetacriticUrl()) || detail.getMetacriticUrl() == null) {
            detail.setMetacriticUrl(payload.getMetacriticUrl());
        }
        if (payload.getAchievementTotal() != null) {
            detail.setAchievementTotal(payload.getAchievementTotal());
        }
        if (payload.getAchievementHighlights() != null && !payload.getAchievementHighlights().isEmpty()
                || detail.getAchievementHighlights() == null) {
            detail.setAchievementHighlights(toAchievements(payload.getAchievementHighlights()));
        }
        if (StringUtils.hasText(payload.getPcRequirementsMin()) || detail.getPcRequirementsMin() == null) {
            detail.setPcRequirementsMin(payload.getPcRequirementsMin());
        }
        if (StringUtils.hasText(payload.getPcRequirementsRec()) || detail.getPcRequirementsRec() == null) {
            detail.setPcRequirementsRec(payload.getPcRequirementsRec());
        }
        detail.setRichSyncedAt(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());
        repository.save(detail);
    }

    /** 将 Steam 截图 payload 转换为 Mongo 子文档。 */
    private List<SteamGameScreenshot> toScreenshots(List<SteamScreenshotPayload> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(item -> {
            SteamGameScreenshot target = new SteamGameScreenshot();
            target.setFullUrl(item.getFullUrl());
            target.setThumbnailUrl(item.getThumbnailUrl());
            return target;
        }).toList();
    }

    /** 将 Steam 视频 payload 转换为 Mongo 子文档。 */
    private List<SteamGameMovie> toMovies(List<SteamMoviePayload> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(item -> {
            SteamGameMovie target = new SteamGameMovie();
            target.setName(item.getName());
            target.setThumbnailUrl(item.getThumbnailUrl());
            target.setMp4Url(item.getMp4Url());
            target.setWebmUrl(item.getWebmUrl());
            return target;
        }).toList();
    }

    /** 将 Steam 成就定义 payload 转换为 Mongo 子文档。 */
    private List<SteamGameAchievement> toAchievements(List<SteamAchievementDefinitionPayload> source) {
        if (source == null) {
            return null;
        }
        return source.stream().map(item -> {
            SteamGameAchievement target = new SteamGameAchievement();
            target.setApiName(item.getApiName());
            target.setName(item.getName());
            target.setDescription(item.getDescription());
            target.setIconUrl(item.getIconUrl());
            return target;
        }).toList();
    }

    /** 判断 Mongo 富详情是否至少具备介绍和一类补充数据。 */
    private boolean isIncomplete(SteamGameDetail detail) {
        boolean hasDescription = StringUtils.hasText(detail.getSteamShortDesc())
                || StringUtils.hasText(detail.getSteamAboutHtml());
        boolean hasSupplement = detail.getScreenshots() != null && !detail.getScreenshots().isEmpty()
                || detail.getMovies() != null && !detail.getMovies().isEmpty()
                || detail.getAchievementTotal() != null
                || detail.getAchievementHighlights() != null && !detail.getAchievementHighlights().isEmpty();
        return !hasDescription || !hasSupplement;
    }
}
