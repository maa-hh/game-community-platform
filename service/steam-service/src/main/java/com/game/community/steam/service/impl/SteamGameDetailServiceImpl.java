package com.game.community.steam.service.impl;

import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.mongo.SteamGameDetail;
import com.game.community.steam.mongo.SteamGameDetailRepository;
import com.game.community.steam.service.SteamGameDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
        detail.setSteamShortDesc(catalog.getSteamShortDesc());
        detail.setSteamAboutHtml(catalog.getSteamAboutHtml());
        detail.setScreenshots(catalog.getSteamScreenshots());
        detail.setMovies(catalog.getSteamMovies());
        detail.setCategories(catalog.getSteamCategories());
        detail.setMetacriticScore(catalog.getMetacriticScore());
        detail.setMetacriticUrl(catalog.getMetacriticUrl());
        detail.setAchievementTotal(catalog.getAchievementTotal());
        detail.setAchievementHighlights(catalog.getAchievementHighlights());
        detail.setPcRequirementsMin(catalog.getPcRequirementsMin());
        detail.setPcRequirementsRec(catalog.getPcRequirementsRec());
        detail.setRichSyncedAt(LocalDateTime.now());
        detail.setUpdateTime(LocalDateTime.now());
        repository.save(detail);
    }
}
