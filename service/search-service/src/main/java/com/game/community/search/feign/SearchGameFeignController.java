package com.game.community.search.feign;

import com.game.community.model.base.Result;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.search.service.GameSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/feign/search")
@RequiredArgsConstructor
public class SearchGameFeignController {

    private final GameSearchService gameSearchService;

    @PostMapping("/games/tags")
    public Result<List<GameTagVO>> listTags(@RequestBody List<Long> appIds) {
        return Result.success(gameSearchService.listTags(appIds));
    }
}
