package com.game.community.steam.feign;

import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.game.GamePageQuery;
import com.game.community.model.dto.game.GameSearchQuery;
import com.game.community.model.dto.steam.SteamAppSearchQuery;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.steam.service.GameCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping(value = "/feign/steam", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
public class SteamFeignController {

    private final GameCatalogService gameCatalogService;

    /** 提供游戏详情给其他微服务。 */
    @GetMapping("/games/{appId}/detail")
    public Result<GameDetailVO> getGameDetail(@PathVariable("appId") Long appId) {
        return Result.success(gameCatalogService.getDetail(appId));
    }

    /** 批量提供游戏标签给其他微服务。 */
    @PostMapping("/games/tags")
    public Result<List<GameTagVO>> listGameTags(@RequestBody List<Long> appIds) {
        return Result.success(gameCatalogService.listTagsByAppIds(appIds));
    }

    /** 批量提供搜索命中游戏的本地目录字段，覆盖 ES 旧索引中的评分和价格。 */
    @PostMapping("/games/catalog-items")
    public Result<List<GameListItemVO>> listCatalogItems(@RequestBody List<Long> appIds) {
        gameCatalogService.refreshMetricsPriceAsync(appIds);
        return Result.success(gameCatalogService.listCatalogItemsByAppIds(appIds));
    }

    /** 批量同步游戏讨论数。 */
    @PostMapping("/games/discuss-count/sync")
    public Result<Void> syncDiscussCount(@RequestBody List<Long> appIds) {
        gameCatalogService.syncDiscussCounts(appIds);
        return Result.success(null);
    }

    /** 提供不落库的 Steam Store 轻量搜索。 */
    @GetMapping("/games/search")
    public Result<List<GameListItemVO>> searchGames(
            @Valid @ModelAttribute SteamAppSearchQuery query) {
        return Result.success(gameCatalogService.searchSteamApps(
                query));
    }

    /** 异步补充 Steam 搜索候选的公共目录和 ES 索引。 */
    @PostMapping("/games/basic-info/enrich")
    public Result<Void> enrichBasicInfo(@RequestBody List<Long> appIds) {
        gameCatalogService.warmupBasicInfoAsync(appIds);
        return Result.success(null);
    }

    /** 提供本地游戏目录搜索给搜索服务。 */
    @GetMapping("/games/catalog-search")
    public PageResult<GameListItemVO> searchCatalogGames(
            @Valid @ModelAttribute GameSearchQuery query) {
        return gameCatalogService.searchGames(query);
    }

    /** 提供搜索服务重建索引所需的游戏分页数据。 */
    @GetMapping("/games/index/page")
    public PageResult<GameListItemVO> pageGameIndex(
            @Valid @ModelAttribute GamePageQuery query) {
        return gameCatalogService.pageGameIndex(query);
    }
}
