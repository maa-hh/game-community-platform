package com.game.community.steam.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.game.GameDiscoverQuery;
import com.game.community.model.dto.game.SaveGameReviewDTO;
import com.game.community.model.vo.game.GameChartItemVO;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameReviewVO;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.GameChartService;
import com.game.community.steam.service.GameDiscoverService;
import com.game.community.steam.service.GameReviewService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameController {

    private final GameCatalogService gameCatalogService;
    private final GameReviewService gameReviewService;
    private final GameChartService gameChartService;
    private final GameDiscoverService gameDiscoverService;

    @GetMapping("/discover")
    public PageResult<GameChartItemVO> discover(
            @RequestParam(value = "board", defaultValue = "all") String board,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "order", defaultValue = "desc") String order,
            @RequestParam(value = "page", defaultValue = "1") Long page,
            @RequestParam(value = "size", defaultValue = "20") Long size,
            @RequestParam(value = "minSteamScore", required = false) Integer minSteamScore,
            @RequestParam(value = "maxSteamScore", required = false) Integer maxSteamScore,
            @RequestParam(value = "minSteamReviews", required = false) Integer minSteamReviews,
            @RequestParam(value = "maxSteamReviews", required = false) Integer maxSteamReviews,
            @RequestParam(value = "minPrice", required = false) Integer minPrice,
            @RequestParam(value = "maxPrice", required = false) Integer maxPrice,
            @RequestParam(value = "minFinalPrice", required = false) Integer minFinalPrice,
            @RequestParam(value = "maxFinalPrice", required = false) Integer maxFinalPrice,
            @RequestParam(value = "minDiscount", required = false) Integer minDiscount,
            @RequestParam(value = "discountOnly", required = false) Boolean discountOnly,
            @RequestParam(value = "freeOnly", required = false) Boolean freeOnly) {
        GameDiscoverQuery query = new GameDiscoverQuery();
        query.setBoard(board);
        query.setSort(sort);
        query.setOrder(order);
        query.setPage(page);
        query.setSize(size);
        query.setMinSteamScore(minSteamScore);
        query.setMaxSteamScore(maxSteamScore);
        query.setMinSteamReviews(minSteamReviews);
        query.setMaxSteamReviews(maxSteamReviews);
        query.setMinPrice(minPrice);
        query.setMaxPrice(maxPrice);
        query.setMinFinalPrice(minFinalPrice);
        query.setMaxFinalPrice(maxFinalPrice);
        query.setMinDiscount(minDiscount);
        query.setDiscountOnly(discountOnly);
        query.setFreeOnly(freeOnly);
        return gameDiscoverService.pageDiscover(query);
    }

    @GetMapping("/chart")
    public Result<List<GameChartItemVO>> chart(
            @RequestParam(value = "board", defaultValue = "hot") String board) {
        return Result.success(gameChartService.listChart(board));
    }

    @GetMapping("/page")
    public PageResult<GameListItemVO> page(@RequestParam(value = "sort", defaultValue = "hot") String sort,
                                          @RequestParam(value = "page", defaultValue = "1") Long page,
                                          @RequestParam(value = "size", defaultValue = "20") Long size) {
        return gameCatalogService.pageGames(sort, page, size);
    }

    @GetMapping("/search")
    public PageResult<GameListItemVO> search(@RequestParam("q") String keyword,
                                             @RequestParam(value = "page", defaultValue = "1") Long page,
                                             @RequestParam(value = "size", defaultValue = "20") Long size) {
        return gameCatalogService.searchGames(keyword, page, size);
    }

    @GetMapping("/{appId}")
    public Result<GameDetailVO> detail(@PathVariable("appId") Long appId) {
        return Result.success(gameCatalogService.getDetail(appId));
    }

    @GetMapping("/{appId}/reviews")
    public PageResult<GameReviewVO> reviews(@PathVariable("appId") Long appId,
                                            @RequestParam(value = "page", defaultValue = "1") Long page,
                                            @RequestParam(value = "size", defaultValue = "10") Long size) {
        return gameReviewService.listReviews(appId, page, size);
    }

    @LoginCheck
    @GetMapping("/{appId}/reviews/mine")
    public Result<GameReviewVO> myReview(@PathVariable("appId") Long appId) {
        return Result.success(gameReviewService.getMine(appId, UserThreadLocal.getUserId()));
    }

    @LoginCheck
    @PutMapping("/{appId}/reviews/mine")
    public Result<Void> saveMyReview(@PathVariable("appId") Long appId,
                                     @Valid @RequestBody SaveGameReviewDTO dto) {
        gameReviewService.saveReview(appId, UserThreadLocal.getUserId(), dto);
        return Result.success(null);
    }

    @LoginCheck
    @DeleteMapping("/{appId}/reviews/mine")
    public Result<Void> deleteMyReview(@PathVariable("appId") Long appId) {
        gameReviewService.deleteMine(appId, UserThreadLocal.getUserId());
        return Result.success(null);
    }
}
