package com.game.community.search.service.impl;

import com.game.community.common.constant.search.SearchConstants;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.search.service.SuggestTermService;
import com.game.community.search.service.SuggestTermService.TermSeed;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/** 统一维护游戏名称、别名到建议词的来源关系，避免同步入口各自复制规则。 */
@Service
@RequiredArgsConstructor
public class GameSuggestionTermSyncService {

    private final SuggestTermService suggestTermService;

    public void sync(GameListItemVO game) {
        if (game == null || game.getAppId() == null) {
            return;
        }
        List<TermSeed> seeds = new ArrayList<>();
        add(seeds, game.getName(), game.getAppId());
        add(seeds, game.getNameZh(), game.getAppId());
        add(seeds, game.getNameEn(), game.getAppId());
        if (game.getAliases() != null) {
            game.getAliases().forEach(alias -> add(seeds, alias, game.getAppId()));
        }
        suggestTermService.replaceGameTerms(game.getAppId(), seeds);
    }

    private void add(List<TermSeed> seeds, String value, Long appId) {
        if (StringUtils.hasText(value)) {
            seeds.add(new TermSeed(value, SearchConstants.SUGGEST_SOURCE_GAME, appId,
                    SearchConstants.WEIGHT_GAME, false));
        }
    }
}
