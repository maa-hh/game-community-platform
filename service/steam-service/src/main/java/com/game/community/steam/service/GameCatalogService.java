package com.game.community.steam.service;

import com.game.community.model.base.PageResult;
import com.game.community.model.dto.game.GamePageQuery;
import com.game.community.model.dto.game.GameSearchQuery;
import com.game.community.model.dto.steam.SteamAppSearchQuery;
import com.game.community.model.entity.game.GameCatalog;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;

import java.util.List;

public interface GameCatalogService {

    /** 查询游戏详情，必要时从 Steam Store 补齐过期目录数据。 */
    GameDetailVO getDetail(Long appId);

    /** 查询尚未写入公共游戏目录基础信息的 AppID。 */
    List<Long> findMissingBasicInfoIds(List<Long> appIds);

    /** 补充游戏公共基础信息，并刷新游戏搜索索引。 */
    void warmupBasicInfo(Long appId);

    /** 异步补充一批游戏公共基础信息，并刷新游戏搜索索引。 */
    void warmupBasicInfoAsync(List<Long> appIds);

    /** 卡片展示后异步刷新过期的 Steam 评分人数和价格。 */
    void refreshMetricsPriceAsync(List<Long> appIds);

    /** 按 AppID 批量读取本地目录，供搜索服务覆盖 ES 延迟字段。 */
    List<GameListItemVO> listCatalogItemsByAppIds(List<Long> appIds);

    /** 批量写入 Steam 榜单或搜索返回的轻量游戏数据，并刷新搜索索引。 */
    void upsertBasicCatalogBatch(List<GameListItemVO> games);

    /** 按排序方式分页查询本地游戏目录。 */
    PageResult<GameListItemVO> pageGames(GamePageQuery query);

    /** 按游戏名称分页搜索本地游戏目录。 */
    PageResult<GameListItemVO> searchGames(GameSearchQuery query);

    /** 调用 Steam Store 轻量搜索，不落完整游戏详情。 */
    List<GameListItemVO> searchSteamApps(SteamAppSearchQuery query);

    /** 分页查询搜索索引所需的游戏轻量信息。 */
    PageResult<GameListItemVO> pageGameIndex(GamePageQuery query);

    /** 将游戏目录实体转换为列表响应对象。 */
    GameListItemVO toListItem(GameCatalog catalog);

    /** 同步单个游戏的讨论数。 */
    void syncDiscussCount(Long appId);

    /** 批量同步游戏讨论数。 */
    void syncDiscussCounts(List<Long> appIds);

    /** 批量查询游戏标签信息。 */
    List<GameTagVO> listTagsByAppIds(List<Long> appIds);
}
