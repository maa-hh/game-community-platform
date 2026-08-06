package com.game.community.recommend.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.vo.article.HotArticleVO;
import com.game.community.recommend.service.HotRankService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/hot-article")
public class HotArticleController {

    private final HotRankService hotRankService;

    @GetMapping("/rank")
    public Result<List<HotArticleVO>> rank(@RequestParam(value = "board", defaultValue = "total") String board,
                                           @RequestParam(value = "categoryId", required = false) Long categoryId,
                                           @RequestParam(value = "periodKey", required = false) String periodKey,
                                           @RequestParam(value = "refresh", defaultValue = "false") boolean refresh) {
        return Result.success(hotRankService.listRank(board, categoryId, periodKey, UserThreadLocal.getUserId(), refresh));
    }

    /** 兼容旧客户端，数据源统一走新版热榜。 */
    @LoginCheck
    @GetMapping("/list")
    public PageResult<HotArticleVO> list(@RequestParam(value = "page", defaultValue = "1") Long page,
                                         @RequestParam(value = "size", defaultValue = "12") Long size) {
        return page(hotRankService.listRank("total", null, null, UserThreadLocal.getUserId(), false), page, size);
    }

    /** 兼容旧客户端，数据源统一走新版热榜。 */
    @LoginCheck
    @GetMapping("/category/{categoryId}")
    public PageResult<HotArticleVO> listByCategory(@PathVariable("categoryId") Long categoryId,
                                                   @RequestParam(value = "page", defaultValue = "1") Long page,
                                                   @RequestParam(value = "size", defaultValue = "12") Long size) {
        return page(hotRankService.listRank("total", categoryId, null, UserThreadLocal.getUserId(), false), page, size);
    }

    private PageResult<HotArticleVO> page(List<HotArticleVO> records, Long pageValue, Long sizeValue) {
        long page = pageValue == null || pageValue < 1 ? 1 : pageValue;
        long size = sizeValue == null || sizeValue < 1 ? 12 : Math.min(sizeValue, 50);
        int start = Math.toIntExact(Math.min((page - 1) * size, records.size()));
        int end = Math.toIntExact(Math.min((long) start + size, records.size()));
        return PageResult.of(records.subList(start, end), page, size, (long) records.size());
    }
}
