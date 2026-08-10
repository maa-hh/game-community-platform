package com.game.community.steam.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.game.GameChartQuery;
import com.game.community.model.dto.game.GameDiscoverQuery;
import com.game.community.model.dto.game.GamePageQuery;
import com.game.community.model.dto.game.GameReviewPageQuery;
import com.game.community.model.dto.game.GameSearchQuery;
import com.game.community.model.dto.game.SaveGameReviewDTO;
import com.game.community.model.vo.game.GameChartItemVO;
import com.game.community.model.vo.game.GameDetailVO;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameReviewVO;
import com.game.community.steam.service.GameCatalogService;
import com.game.community.steam.service.GameChartService;
import com.game.community.steam.service.GameDiscoverService;
import com.game.community.steam.service.GameReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** 分页查询游戏发现数据，支持榜单、排序和价格筛选。 */
    @GetMapping("/discover")
    public PageResult<GameChartItemVO> discover(
            @Valid @ModelAttribute GameDiscoverQuery query) {
        return gameDiscoverService.pageDiscover(query);
    }

    /** 查询当前榜单快照。 */
    @GetMapping("/chart")
    public Result<List<GameChartItemVO>> chart(
            @Valid @ModelAttribute GameChartQuery query) {
        return Result.success(gameChartService.listChart(query));
    }

    /** 分页查询本地游戏目录。 */
    @GetMapping("/page")
    public PageResult<GameListItemVO> page(
            @Valid @ModelAttribute GamePageQuery query) {
        return gameCatalogService.pageGames(query);
    }

    /** 按名称搜索本地游戏目录。 */
    @GetMapping("/search")
    public PageResult<GameListItemVO> search(
            @Valid @ModelAttribute GameSearchQuery query) {
        return gameCatalogService.searchGames(query);
    }

    /** 查询游戏详情。 */
    @GetMapping("/{appId}")
    public Result<GameDetailVO> detail(@PathVariable("appId") Long appId) {
        return Result.success(gameCatalogService.getDetail(appId));
    }

    /** 分页查询游戏公开短评。 */
    @GetMapping("/{appId}/reviews")
    public PageResult<GameReviewVO> reviews(@PathVariable("appId") Long appId,
                                            @Valid @ModelAttribute
                                            GameReviewPageQuery query) {
        query.setAppId(appId);
        return gameReviewService.listReviews(query);
    }

    /** 查询当前登录用户对指定游戏的短评。 */
    @LoginCheck
    @GetMapping("/{appId}/reviews/mine")
    public Result<GameReviewVO> myReview(@PathVariable("appId") Long appId) {
        return Result.success(gameReviewService.getMine(appId));
    }

    /** 保存当前登录用户对指定游戏的评分和短评。 */
    @LoginCheck
    @PutMapping("/{appId}/reviews/mine")
    public Result<Void> saveMyReview(@PathVariable("appId") Long appId,
                                     @Valid @RequestBody SaveGameReviewDTO dto) {
        gameReviewService.saveReview(appId, dto);
        return Result.success(null);
    }

    /** 软删除当前登录用户对指定游戏的短评。 */
    @LoginCheck
    @DeleteMapping("/{appId}/reviews/mine")
    public Result<Void> deleteMyReview(@PathVariable("appId") Long appId) {
        gameReviewService.deleteMine(appId);
        return Result.success(null);
    }
}
