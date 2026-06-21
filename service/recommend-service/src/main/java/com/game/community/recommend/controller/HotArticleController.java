package com.game.community.recommend.controller;

import com.game.community.common.annotation.LoginCheck;
import com.game.community.model.base.PageResult;
import com.game.community.model.vo.article.HotArticleVO;
import com.game.community.recommend.service.HotArticleService;
import com.game.community.utils.ThreadLocal.UserThreadLocal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/hot-article")
public class HotArticleController {

    private final HotArticleService hotArticleService;

    @LoginCheck
    @GetMapping("/list")
    public PageResult<HotArticleVO> list(@RequestParam(value = "page", defaultValue = "1") Long page,
                                         @RequestParam(value = "size", defaultValue = "12") Long size) {
        return hotArticleService.listHotArticles(UserThreadLocal.getUserId(), page, size);
    }

    @LoginCheck
    @GetMapping("/category/{categoryId}")
    public PageResult<HotArticleVO> listByCategory(@PathVariable("categoryId") Long categoryId,
                                                   @RequestParam(value = "page", defaultValue = "1") Long page,
                                                   @RequestParam(value = "size", defaultValue = "12") Long size) {
        return hotArticleService.listCategoryHotArticles(UserThreadLocal.getUserId(), categoryId, page, size);
    }
}
