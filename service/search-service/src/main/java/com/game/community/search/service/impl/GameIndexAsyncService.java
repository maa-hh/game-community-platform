package com.game.community.search.service.impl;

import com.game.community.feign.SteamFeignClient;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.search.service.ElasticsearchService;
import com.game.community.search.service.SuggestTermService;
import com.game.community.search.service.SuggestTermService.TermSeed;
import com.game.community.common.constant.search.SearchConstants;
import org.springframework.util.StringUtils;
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
    private final SteamFeignClient steamFeignClient;
    private final SuggestTermService suggestTermService;

    @Async("gameIndexExecutor")
    public void indexCandidates(List<GameListItemVO> candidates) {
        if (candidates == null) {
            return;
        }
        candidates.forEach(item -> {
            try {
                elasticsearchService.indexGame(item);
                syncGameSuggestionTerms(item);
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

    /** 为搜索回源发现的游戏补充建议词来源，保证下一次输入即可命中候选。 */
    private void syncGameSuggestionTerms(GameListItemVO game) {
        if (game == null || game.getAppId() == null) {
            return;
        }
        List<TermSeed> seeds = new java.util.ArrayList<>();
        addGameTerm(seeds, game.getName(), game.getAppId());
        addGameTerm(seeds, game.getNameZh(), game.getAppId());
        addGameTerm(seeds, game.getNameEn(), game.getAppId());
        if (game.getAliases() != null) {
            game.getAliases().forEach(alias -> addGameTerm(seeds, alias, game.getAppId()));
        }
        suggestTermService.replaceGameTerms(game.getAppId(), seeds);
    }

    /** 添加非空游戏名称作为建议词候选。 */
    private void addGameTerm(List<TermSeed> seeds, String value, Long appId) {
        if (StringUtils.hasText(value)) {
            seeds.add(new TermSeed(value, SearchConstants.SUGGEST_SOURCE_GAME, appId,
                    SearchConstants.WEIGHT_GAME, false));
        }
    }
}
