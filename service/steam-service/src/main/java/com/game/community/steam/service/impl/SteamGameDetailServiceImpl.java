package com.game.community.steam.service.impl;

import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.mongo.SteamGameDetail;
import com.game.community.steam.mongo.SteamGameDetailRepository;
import com.game.community.steam.service.SteamGameDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SteamGameDetailServiceImpl implements SteamGameDetailService {

    private static final long RICH_DETAIL_TTL_DAYS = 7;

    private final SteamGameDetailRepository repository;

    @Override
    public SteamGameDetail find(Long appId) {
        return appId == null ? null : repository.findByAppId(appId).orElse(null);
    }

    @Override
    public boolean needsRefresh(SteamGameDetail detail) {
        return detail == null
                || isIncomplete(detail)
                || detail.getRichSyncedAt() == null
                || detail.getRichSyncedAt().isBefore(LocalDateTime.now().minusDays(RICH_DETAIL_TTL_DAYS));
    }

    @Override
    public void saveFromCatalog(GameCatalog catalog) {
        if (catalog == null || catalog.getAppId() == null) {
            return;
        }
        SteamGameDetail detail = repository.findByAppId(catalog.getAppId()).orElseGet(SteamGameDetail::new);
        detail.setAppId(catalog.getAppId());
        if (StringUtils.hasText(catalog.getSteamShortDesc()) || detail.getSteamShortDesc() == null) {
            detail.setSteamShortDesc(catalog.getSteamShortDesc());
        }
        if (StringUtils.hasText(catalog.getSteamAboutHtml()) || detail.getSteamAboutHtml() == null) {
            detail.setSteamAboutHtml(catalog.getSteamAboutHtml());
        }
        if (catalog.getSteamScreenshots() != null && !catalog.getSteamScreenshots().isEmpty()
                || detail.getScreenshots() == null) {
            detail.setScreenshots(catalog.getSteamScreenshots());
        }
        if (catalog.getSteamMovies() != null && !catalog.getSteamMovies().isEmpty()
                || detail.getMovies() == null) {
            detail.setMovies(catalog.getSteamMovies());
        }
        if (catalog.getSteamCategories() != null && !catalog.getSteamCategories().isEmpty()
                || detail.getCategories() == null) {
            detail.setCategories(catalog.getSteamCategories());
        }
        if (catalog.getMetacriticScore() != null) {
            detail.setMetacriticScore(catalog.getMetacriticScore());
        }
        if (StringUtils.hasText(catalog.getMetacriticUrl()) || detail.getMetacriticUrl() == null) {
            detail.setMetacriticUrl(catalog.getMetacriticUrl());
        }
        if (catalog.getAchievementTotal() != null) {
            detail.setAchievementTotal(catalog.getAchievementTotal());
        }
        if (catalog.getAchievementHighlights() != null && !catalog.getAchievementHighlights().isEmpty()
                || detail.getAchievementHighlights() == null) {
            detail.setAchievementHighlights(catalog.getAchievementHighlights());
        }
        if (StringUtils.hasText(catalog.getPcRequirementsMin()) || detail.getPcRequirementsMin() == null) {
            detail.setPcRequirementsMin(catalog.getPcRequirementsMin());
        }
        if (StringUtils.hasText(catalog.getPcRequirementsRec()) || detail.getPcRequirementsRec() == null) {
            detail.setPcRequirementsRec(catalog.getPcRequirementsRec());
        }
        detail.setRichSyncedAt(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());
        repository.save(detail);
    }

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
