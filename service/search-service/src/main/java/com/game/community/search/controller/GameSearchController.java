package com.game.community.search.controller;

import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.game.GameSearchQuery;
import com.game.community.model.vo.game.GameListItemVO;
import com.game.community.model.vo.game.GameTagVO;
import com.game.community.search.service.GameSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class GameSearchController {

    private final GameSearchService gameSearchService;

    @GetMapping("/game")
    public PageResult<GameListItemVO> searchGame(@ModelAttribute GameSearchQuery query) {
        return gameSearchService.search(query.getKeyword(), query.getPage(), query.getSize());
    }

    @PostMapping("/games/tags")
    public Result<List<GameTagVO>> listTags(@RequestBody List<Long> appIds) {
        return Result.success(gameSearchService.listTags(appIds));
    }
}
