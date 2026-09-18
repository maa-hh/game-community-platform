package com.game.community.recommend.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.recommend.HotRankQueryDTO;
import com.game.community.model.json.ApiJsonViews;
import com.game.community.model.vo.article.HotArticleVO;
import com.fasterxml.jackson.annotation.JsonView;
import com.game.community.recommend.service.HotRankService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/hot-article")
public class HotArticleController {

    private final HotRankService hotRankService;

    @GetMapping("/rank")
    @JsonView(ApiJsonViews.Public.class)
    public Result<List<HotArticleVO>> rank(@Valid HotRankQueryDTO query) {
        return Result.success(hotRankService.listRank(query, UserThreadLocal.getUserId()));
    }

    /** 兼容旧客户端，数据源统一走新版热榜。 */
    @LoginCheck
    @GetMapping("/list")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<HotArticleVO> list(@RequestParam(value = "page", defaultValue = "1") Long page,
                                         @RequestParam(value = "size", defaultValue = "12") Long size) {
        HotRankQueryDTO query = new HotRankQueryDTO();
        return page(hotRankService.listRank(query, UserThreadLocal.getUserId()), page, size);
    }

    /** 兼容旧客户端，数据源统一走新版热榜。 */
    @LoginCheck
    @GetMapping("/category/{categoryId}")
    @JsonView(ApiJsonViews.Public.class)
    public PageResult<HotArticleVO> listByCategory(@PathVariable("categoryId") Long categoryId,
                                                   @RequestParam(value = "page", defaultValue = "1") Long page,
                                                   @RequestParam(value = "size", defaultValue = "12") Long size) {
        HotRankQueryDTO query = new HotRankQueryDTO();
        query.setCategoryId(categoryId);
        return page(hotRankService.listRank(query, UserThreadLocal.getUserId()), page, size);
    }

    private PageResult<HotArticleVO> page(List<HotArticleVO> records, Long pageValue, Long sizeValue) {
        long page = pageValue == null || pageValue < 1 ? 1 : pageValue;
        long size = sizeValue == null || sizeValue < 1 ? 12 : Math.min(sizeValue, 50);
        int start = Math.toIntExact(Math.min((page - 1) * size, records.size()));
        int end = Math.toIntExact(Math.min((long) start + size, records.size()));
        return PageResult.of(records.subList(start, end), page, size, (long) records.size());
    }
}
