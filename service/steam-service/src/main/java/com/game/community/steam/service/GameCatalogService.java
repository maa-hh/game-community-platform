package com.game.community.steam.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;

import java.util.List;

public interface GameCatalogService {

    GameDetailVO getDetail(Long appId);

    /** 后台预热目录，不阻塞榜单或搜索请求。 */
    void warmup(Long appId);

    PageResult<GameListItemVO> pageGames(String sort, Long page, Long size);

    PageResult<GameListItemVO> searchGames(String keyword, Long page, Long size);

    List<GameListItemVO> searchSteamApps(String keyword, int start, int size);

    PageResult<GameListItemVO> pageGameIndex(Long page, Long size);

    GameListItemVO toListItem(GameCatalog catalog);

    void syncDiscussCount(Long appId);

    void syncDiscussCounts(List<Long> appIds);

    List<GameTagVO> listTagsByAppIds(List<Long> appIds);
}
