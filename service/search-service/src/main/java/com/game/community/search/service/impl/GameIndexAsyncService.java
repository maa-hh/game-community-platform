package com.game.community.search.service.impl;

import com.game.community.feign.SteamFeignClient;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameIndexAsyncService {

    private final ElasticsearchService elasticsearchService;
    private final SteamFeignClient steamFeignClient;
    private final GameSuggestionTermSyncService gameSuggestionTermSyncService;

    @Async("gameIndexExecutor")
    public void indexCandidates(List<GameListItemVO> candidates) {
        if (candidates == null) {
            return;
        }
        candidates.stream().filter(Objects::nonNull).forEach(item -> {
            try {
                elasticsearchService.indexGame(item);
                gameSuggestionTermSyncService.sync(item);
            } catch (Exception e) {
                log.warn("Steam fallback 游戏写入 ES 失败: appId={}", item.getAppId(), e);
            }
        });
    }

    /** 异步通知 Steam 服务补充候选游戏的公共目录和 ES 索引。 */
    @Async("gameIndexExecutor")
    public void enrichCatalog(List<GameListItemVO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        List<Long> appIds = candidates.stream()
                .filter(item -> item != null && item.getAppId() != null)
                .map(GameListItemVO::getAppId)
                .distinct()
                .toList();
        if (appIds.isEmpty()) {
            return;
        }
        try {
            steamFeignClient.enrichBasicInfo(appIds);
        } catch (Exception e) {
            log.warn("Steam 搜索候选公共目录补充请求失败: appIds={}", appIds, e);
        }
    }

}
