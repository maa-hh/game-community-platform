package com.game.community.search.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;

import java.time.LocalDateTime;
import java.util.List;

public interface GameSearchService {

    PageResult<GameListItemVO> search(String keyword, Long page, Long size);

    List<GameTagVO> listTags(List<Long> appIds);

    void index(GameListItemVO game);

    void index(GameListItemVO game, LocalDateTime eventTime);

    void delete(Long appId);

    void rebuildFromSteam();
}
