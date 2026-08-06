package com.game.community.danmaku.controller;

import com.game.community.model.base.Result;
import com.game.community.model.vo.danmaku.DanmakuVO;
import com.game.community.danmaku.service.DanmakuQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/danmaku")
@RequiredArgsConstructor
public class DanmakuController {

    private final DanmakuQueryService queryService;

    @GetMapping("/history/{videoPublicId}")
    public Result<List<DanmakuVO>> history(@PathVariable("videoPublicId") String videoPublicId,
                                           @RequestParam(value = "fromMs", required = false) Long fromMs,
                                           @RequestParam(value = "toMs", required = false) Long toMs,
                                           @RequestParam(value = "limit", required = false) Integer limit) {
        return Result.success(queryService.history(videoPublicId, fromMs, toMs, limit));
    }
}
