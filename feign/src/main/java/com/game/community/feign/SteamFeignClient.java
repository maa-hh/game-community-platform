package com.game.community.feign;

import com.game.community.model.base.Result;
import com.game.community.model.base.PageResult;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "steam-service", contextId = "steamFeignClient", path = "/feign/steam")
public interface SteamFeignClient {

    @GetMapping("/games/{appId}/detail")
    Result<GameDetailVO> getGameDetail(@PathVariable("appId") Long appId);

    @PostMapping("/games/tags")
    Result<List<GameTagVO>> listGameTags(@RequestBody List<Long> appIds);

    @PostMapping("/games/discuss-count/sync")
    Result<Void> syncDiscussCount(@RequestBody List<Long> appIds);

    /** 轻量 Steam Store 搜索，不触发完整游戏详情抓取。 */
    @GetMapping("/games/search")
    Result<List<GameListItemVO>> searchGames(@RequestParam("q") String keyword,
                                             @RequestParam("start") Integer start,
                                             @RequestParam("size") Integer size);

    /** 搜索 Steam 服务本地游戏库，避免 ES 未建索引时直接退化为模糊 Store 搜索。 */
    @GetMapping("/games/catalog-search")
    PageResult<GameListItemVO> searchCatalogGames(@RequestParam("q") String keyword,
                                                  @RequestParam("page") Integer page,
                                                  @RequestParam("size") Integer size);

    /** 搜索服务启动时重建已有游戏轻量索引。 */
    @GetMapping("/games/index/page")
    PageResult<GameListItemVO> listGameIndexPage(@RequestParam("page") Integer page,
                                                 @RequestParam("size") Integer size);
}
