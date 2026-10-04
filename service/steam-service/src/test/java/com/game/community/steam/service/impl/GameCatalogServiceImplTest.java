package com.game.community.steam.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.common.constant.steam.SteamRedisConstants;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.steam.client.SteamChartClient;
import com.game.community.steam.client.SteamStoreClient;
import com.game.community.steam.event.GameSearchIndexProducer;
import com.game.community.steam.mapper.GameCatalogMapper;
import com.game.community.steam.mapper.GameReviewMapper;
import com.game.community.steam.service.SteamGameDetailService;
import com.game.community.utils.RedisUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameCatalogServiceImplTest {

    @Mock
    private GameCatalogMapper gameCatalogMapper;
    @Mock
    private SteamStoreClient steamStoreClient;
    @Mock
    private SteamChartClient steamChartClient;
    @Mock
    private ContentFeignClient contentFeignClient;
    @Mock
    private GameReviewMapper gameReviewMapper;
    @Mock
    private RedisUtils redisUtils;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @Mock
    private GameSearchIndexProducer gameSearchIndexProducer;
    @Mock
    private SteamGameDetailService steamGameDetailService;
    @Mock
    private SteamGameDetailRefreshService steamGameDetailRefreshService;
    @Mock
    private SteamCatalogMetricsRefreshService steamCatalogMetricsRefreshService;

    @InjectMocks
    private GameCatalogServiceImpl service;

    /** 新鲜完整缓存直接返回，不能每次命中都提交 Steam 富详情刷新。 */
    @Test
    void getDetailShouldNotRefreshFreshCompleteCache() throws Exception {
        long appId = 10L;
        GameDetailVO cached = new GameDetailVO();
        cached.setAppId(appId);
        cached.setName("Counter-Strike");
        cached.setShortDescription("description");
        cached.setAchievementTotal(0);
        cached.setDetailReady(true);
        cached.setSteamSyncedAt(LocalDateTime.now());
        String cachedJson = objectMapper.writeValueAsString(cached);
        when(redisUtils.get(SteamRedisConstants.GAME_DETAIL_KEY_PREFIX + appId))
                .thenReturn(cachedJson);

        GameDetailVO result = service.getDetail(appId);

        assertThat(result.getName()).isEqualTo("Counter-Strike");
        verify(steamCatalogMetricsRefreshService).refreshIfStaleAsync(List.of(appId));
        verify(steamGameDetailRefreshService, never()).refresh(appId);
        verifyNoInteractions(gameCatalogMapper, steamGameDetailService);
    }

    /** 待补全目录一次请求只读一次本地存储并提交一次后台刷新。 */
    @Test
    void getDetailShouldSubmitOneRefreshForPendingCatalog() {
        long appId = 20L;
        GameCatalog catalog = new GameCatalog();
        catalog.setAppId(appId);
        catalog.setDisplayName("Pending game");
        catalog.setDetailReady(false);
        catalog.setSteamSyncedAt(LocalDateTime.now());
        catalog.setDiscussCount(0);
        catalog.setReviewCount(0);
        catalog.setAvgScore(BigDecimal.ZERO);
        when(gameCatalogMapper.selectById(appId)).thenReturn(catalog);
        when(steamGameDetailService.needsRefresh(null)).thenReturn(true);

        GameDetailVO result = service.getDetail(appId);

        assertThat(result.getDetailReady()).isFalse();
        verify(gameCatalogMapper, times(1)).selectById(appId);
        verify(steamGameDetailService, times(1)).find(appId);
        verify(steamGameDetailRefreshService, times(1)).refresh(appId);
        verify(steamCatalogMetricsRefreshService, times(1))
                .refreshIfStaleAsync(List.of(appId));
        verifyNoInteractions(steamStoreClient, steamChartClient);
    }
}
