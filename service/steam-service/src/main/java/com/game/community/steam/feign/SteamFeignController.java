package com.game.community.steam.feign;

import com.game.community.model.base.Result;
import com.game.community.model.base.PageResult;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.steam.service.GameCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/feign/steam", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
@RequiredArgsConstructor
public class SteamFeignController {

    private final GameCatalogService gameCatalogService;

    @GetMapping("/games/{appId}/detail")
    public Result<GameDetailVO> getGameDetail(@PathVariable("appId") Long appId) {
        return Result.success(gameCatalogService.getDetail(appId));
    }

    @PostMapping("/games/tags")
    public Result<List<GameTagVO>> listGameTags(@RequestBody List<Long> appIds) {
        return Result.success(gameCatalogService.listTagsByAppIds(appIds));
    }

    @PostMapping("/games/discuss-count/sync")
    public Result<Void> syncDiscussCount(@RequestBody List<Long> appIds) {
        gameCatalogService.syncDiscussCounts(appIds);
        return Result.success(null);
    }

    @GetMapping("/games/search")
    public Result<List<com.game.community.model.vo.game.GameListItemVO>> searchGames(
            @RequestParam("q") String keyword,
            @RequestParam("start") Integer start,
            @RequestParam("size") Integer size) {
        return Result.success(gameCatalogService.searchSteamApps(keyword,
                start == null ? 0 : start, size == null ? 20 : size));
    }

    @GetMapping("/games/catalog-search")
    public PageResult<com.game.community.model.vo.game.GameListItemVO> searchCatalogGames(
            @RequestParam("q") String keyword,
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size) {
        return gameCatalogService.searchGames(keyword,
                page == null ? 1L : page.longValue(), size == null ? 20L : size.longValue());
    }

    @GetMapping("/games/index/page")
    public PageResult<com.game.community.model.vo.game.GameListItemVO> pageGameIndex(
            @RequestParam("page") Integer page,
            @RequestParam("size") Integer size) {
        return gameCatalogService.pageGameIndex(page == null ? 1L : page.longValue(),
                size == null ? 200L : size.longValue());
    }
}
