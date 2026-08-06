package com.game.community.search.service.impl;

import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.search.service.ElasticsearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameIndexAsyncService {

    private final ElasticsearchService elasticsearchService;

    @Async("gameIndexExecutor")
    public void indexCandidates(List<GameListItemVO> candidates) {
        if (candidates == null) {
            return;
        }
        candidates.forEach(item -> {
            try {
                elasticsearchService.indexGame(item);
            } catch (Exception e) {
                log.warn("Steam fallback 游戏写入 ES 失败: appId={}", item.getAppId(), e);
            }
        });
    }
}
